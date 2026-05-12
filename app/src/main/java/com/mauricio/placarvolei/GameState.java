package com.mauricio.placarvolei;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado completo de uma partida de vôlei + lógica de regras.
 *
 * Acesso via singleton {@link #getInstance()}. Modificações disparam {@link Listener}.
 *
 * ----------------------------------------------------------------------------
 * REGRAS IMPLEMENTADAS
 * ----------------------------------------------------------------------------
 *  - Set vence quem atinge target (15 ou 25 conforme {@link ScoringMode}) com
 *    diferença mínima de 2 pontos.
 *  - Se chegar no target sem 2 de diferença, continua (deuce) até alguém abrir 2.
 *  - Detecção é automática, mas o fechamento exige confirmação manual:
 *    {@link #pendingSetClose} fica true; UI mostra modal; usuário chama
 *    {@link #confirmSetClose()} ou {@link #rejectSetClose()}.
 *  - rejectSetClose desfaz o último ponto (caso de clique acidental).
 *  - Saque: rally point. Quem ganha o ponto saca o próximo. Início da partida:
 *    Time A. Início dos sets seguintes: mantém último sacador do set anterior.
 *  - Partida termina quando algum time atinge (bestOf+1)/2 sets.
 *  - newMatch() preserva bestOf e scoringMode; resetMatch() volta a defaults.
 *
 * ----------------------------------------------------------------------------
 * CASOS DE TESTE MANUAL (validar via main ou Instrumented Test futuro)
 * ----------------------------------------------------------------------------
 *  1. FIVB_DEFAULT, bestOf=5, A faz 25-23: pendingSetClose=true, target=25, ok.
 *  2. FIVB_DEFAULT, bestOf=5, A faz 25-24: pendingSetClose=false (sem diff 2),
 *     continua. A faz 26-24: pendingSetClose=true.
 *  3. Deuce: 24-24 → 25-24 → 25-25 → 26-25 → 26-26 → 27-26 → 27-27 → 28-27 →
 *     28-28 → 29-28 → 30-28 → pendingSetClose=true.
 *  4. FIVB_DEFAULT, bestOf=5, sets 2-2, set atual é decisivo: getCurrentSetTarget()=15.
 *  5. ALL_25, bestOf=3, sets 1-1, decisivo: target ainda 25.
 *  6. ALL_15: target sempre 15 em todos os sets.
 *  7. rejectSetClose: estava 25-23 pendingClose. reject → 24-23, pendingClose=false.
 *  8. Troca scoringMode no meio: estado atual = 23-22 modo ALL_25. Muda pra ALL_15.
 *     Próximo ponto: 23 já > target 15 mas só checa após addPoint. addPointA → 24-22:
 *     já passou target 15 + diff 2 = pendingClose true. (regra aplica imediatamente).
 *  9. Saque inicial: A. A faz ponto → A continua sacando. B faz ponto → B saca.
 * 10. matchFinished: bestOf=3, A vence sets 1 e 2 → matchFinished=true, winner=teamAName.
 *     addPoint subsequente deve ser ignorado.
 */
public class GameState {

    public interface Listener {
        void onStateChanged(GameState state);
    }

    // ---------- Singleton ----------
    private static GameState instance;

    public static synchronized GameState getInstance() {
        if (instance == null) {
            instance = new GameState();
        }
        return instance;
    }

    /** Substitui instância (usado pelo repositório ao restaurar do disco). */
    public static synchronized void setInstance(GameState newState) {
        instance = newState;
    }

    // ---------- Estado ----------
    private String teamAName = "Time A";
    private String teamBName = "Time B";
    private int teamAPoints = 0;
    private int teamBPoints = 0;
    private int teamASets = 0;
    private int teamBSets = 0;
    private int bestOf = 5;
    private ScoringMode scoringMode = ScoringMode.FIVB_DEFAULT;
    private boolean teamAServing = true; // A começa sacando
    private boolean pendingSetClose = false;
    private long currentSetStartTime = System.currentTimeMillis();
    private List<SetResult> history = new ArrayList<>();
    private boolean matchFinished = false;
    private String winner = null;
    /** "dark" ou "light". Tema visual da TV + controle. */
    private String theme = "dark";

    // último time que pontuou (pra rejectSetClose desfazer o ponto certo)
    // 'A' ou 'B' ou ' ' (nenhum)
    private char lastScorer = ' ';

    private transient final List<Listener> listeners = new ArrayList<>();

    // ---------- Listeners ----------
    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : listeners) l.onStateChanged(this);
    }

    // ---------- Pontuação ----------
    public synchronized void addPointA() {
        if (matchFinished || pendingSetClose) return;
        teamAPoints++;
        teamAServing = true;
        lastScorer = 'A';
        checkPendingSetClose();
        notifyChanged();
    }

    public synchronized void addPointB() {
        if (matchFinished || pendingSetClose) return;
        teamBPoints++;
        teamAServing = false;
        lastScorer = 'B';
        checkPendingSetClose();
        notifyChanged();
    }

    public synchronized void subtractPointA() {
        if (matchFinished) return;
        if (teamAPoints > 0) {
            teamAPoints--;
            pendingSetClose = false;
            lastScorer = ' '; // subtract manual invalida histórico de "último a pontuar"
            notifyChanged();
        }
    }

    public synchronized void subtractPointB() {
        if (matchFinished) return;
        if (teamBPoints > 0) {
            teamBPoints--;
            pendingSetClose = false;
            lastScorer = ' ';
            notifyChanged();
        }
    }

    // ---------- Fim de set ----------
    /**
     * Verifica se a condição de fim de set foi atingida. Se sim, marca
     * pendingSetClose=true. NÃO fecha sozinho — espera confirmação.
     */
    public void checkPendingSetClose() {
        int target = getCurrentSetTarget();
        int a = teamAPoints;
        int b = teamBPoints;
        boolean aWins = a >= target && (a - b) >= 2;
        boolean bWins = b >= target && (b - a) >= 2;
        pendingSetClose = aWins || bWins;
    }

    /** Confirma fechamento do set pendente. */
    public synchronized void confirmSetClose() {
        if (!pendingSetClose || matchFinished) return;

        String setWinner = (teamAPoints > teamBPoints) ? teamAName : teamBName;
        history.add(new SetResult(teamAPoints, teamBPoints, setWinner));

        if (teamAPoints > teamBPoints) teamASets++;
        else teamBSets++;

        teamAPoints = 0;
        teamBPoints = 0;
        pendingSetClose = false;
        currentSetStartTime = System.currentTimeMillis();
        lastScorer = ' ';

        // checa fim de partida
        int setsToWin = (bestOf + 1) / 2;
        if (teamASets >= setsToWin) {
            matchFinished = true;
            winner = teamAName;
        } else if (teamBSets >= setsToWin) {
            matchFinished = true;
            winner = teamBName;
        }

        // saque do próximo set mantém quem estava sacando no último ponto do anterior
        // (teamAServing já está corretamente setado pelo último addPoint)

        notifyChanged();
    }

    /** Rejeita fechamento — foi clique acidental. Desfaz o último ponto. */
    public synchronized void rejectSetClose() {
        if (!pendingSetClose) return;
        if (lastScorer == 'A' && teamAPoints > 0) teamAPoints--;
        else if (lastScorer == 'B' && teamBPoints > 0) teamBPoints--;
        pendingSetClose = false;
        notifyChanged();
    }

    // ---------- Saque ----------
    public synchronized void toggleServer() {
        teamAServing = !teamAServing;
        notifyChanged();
    }

    // ---------- Configuração ----------
    public synchronized void renameTeam(String newA, String newB) {
        String oldA = teamAName;
        String oldB = teamBName;
        if (newA != null && !newA.trim().isEmpty()) teamAName = newA.trim();
        if (newB != null && !newB.trim().isEmpty()) teamBName = newB.trim();
        // se algum time tinha nome de vencedor, atualiza
        if (matchFinished && winner != null) {
            if (winner.equals(oldA)) winner = teamAName;
            else if (winner.equals(oldB)) winner = teamBName;
        }
        notifyChanged();
    }

    public synchronized void setBestOf(int n) {
        // só ímpar e >= 1
        if (n < 1 || n % 2 == 0) return;
        bestOf = n;
        // troca pode ter feito alguém já ter vencido (ex: 5→3 com 2 sets já ganhos)
        recomputeMatchFinished();
        // ou pode ter mudado se o set atual é decisivo (afeta target em FIVB_DEFAULT)
        if (!matchFinished) checkPendingSetClose();
        notifyChanged();
    }

    /** Recalcula matchFinished com base nos sets atuais. Idempotente. */
    private void recomputeMatchFinished() {
        int setsToWin = (bestOf + 1) / 2;
        if (teamASets >= setsToWin) {
            matchFinished = true;
            winner = teamAName;
        } else if (teamBSets >= setsToWin) {
            matchFinished = true;
            winner = teamBName;
        } else {
            matchFinished = false;
            winner = null;
        }
    }

    public synchronized void setScoringMode(ScoringMode mode) {
        if (mode == null) return;
        scoringMode = mode;
        // re-checa porque o target pode ter mudado e talvez já bata condição de fim
        if (!matchFinished) checkPendingSetClose();
        notifyChanged();
    }

    // ---------- Ciclo de partida ----------
    /** Inicia nova partida preservando bestOf e scoringMode. */
    public synchronized void newMatch() {
        teamAPoints = 0;
        teamBPoints = 0;
        teamASets = 0;
        teamBSets = 0;
        teamAServing = true;
        pendingSetClose = false;
        currentSetStartTime = System.currentTimeMillis();
        history = new ArrayList<>();
        matchFinished = false;
        winner = null;
        lastScorer = ' ';
        notifyChanged();
    }

    /** Reset total: volta tudo a defaults, inclusive nomes/config. */
    public synchronized void resetMatch() {
        teamAName = "Time A";
        teamBName = "Time B";
        bestOf = 5;
        scoringMode = ScoringMode.FIVB_DEFAULT;
        newMatch();
    }

    // ---------- Cálculos de regra ----------
    /** Retorna 25 ou 15 conforme scoringMode e se o set atual é decisivo. */
    public int getCurrentSetTarget() {
        switch (scoringMode) {
            case ALL_25: return 25;
            case ALL_15: return 15;
            case FIVB_DEFAULT:
            default:
                return isDecidingSet() ? 15 : 25;
        }
    }

    /**
     * Set atual é decisivo se ambos os times estão a 1 set de vencer.
     * Ex: bestOf=5, setsToWin=3, decisivo é o 5º (2-2).
     */
    public boolean isDecidingSet() {
        int setsToWin = (bestOf + 1) / 2;
        return teamASets == (setsToWin - 1) && teamBSets == (setsToWin - 1);
    }

    // ---------- Restauração (usado pelo Repository) ----------
    /**
     * Copia campos de outro GameState pra este, preservando listeners.
     * Usado após desserialização do disco.
     */
    public synchronized void restoreFrom(GameState src) {
        if (src == null) return;
        this.teamAName = src.teamAName;
        this.teamBName = src.teamBName;
        this.teamAPoints = src.teamAPoints;
        this.teamBPoints = src.teamBPoints;
        this.teamASets = src.teamASets;
        this.teamBSets = src.teamBSets;
        this.bestOf = src.bestOf;
        this.scoringMode = src.scoringMode != null ? src.scoringMode : ScoringMode.FIVB_DEFAULT;
        this.teamAServing = src.teamAServing;
        this.pendingSetClose = src.pendingSetClose;
        this.currentSetStartTime = src.currentSetStartTime;
        this.history = src.history != null ? src.history : new ArrayList<SetResult>();
        this.matchFinished = src.matchFinished;
        this.winner = src.winner;
        this.lastScorer = src.lastScorer;
        this.theme = (src.theme != null) ? src.theme : "dark";
        notifyChanged();
    }

    // ---------- Getters (sem setters — mutação só via métodos acima) ----------
    public String getTeamAName() { return teamAName; }
    public String getTeamBName() { return teamBName; }
    public int getTeamAPoints() { return teamAPoints; }
    public int getTeamBPoints() { return teamBPoints; }
    public int getTeamASets() { return teamASets; }
    public int getTeamBSets() { return teamBSets; }
    public int getBestOf() { return bestOf; }
    public ScoringMode getScoringMode() { return scoringMode; }
    public boolean isTeamAServing() { return teamAServing; }
    public boolean isPendingSetClose() { return pendingSetClose; }
    public long getCurrentSetStartTime() { return currentSetStartTime; }
    public List<SetResult> getHistory() { return history; }
    public boolean isMatchFinished() { return matchFinished; }
    public String getWinner() { return winner; }
    public String getTheme() { return theme != null ? theme : "dark"; }

    public synchronized void setTheme(String t) {
        if ("light".equals(t) || "dark".equals(t)) {
            theme = t;
            notifyChanged();
        }
    }
}
