package com.mauricio.placarvolei;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity principal — exibe placar em tela cheia na TV.
 *
 * Observa {@link GameState} via Listener e atualiza views.
 * Persistência via {@link GameStateRepository} no onCreate (load) e a cada mudança (save).
 *
 * Nesta etapa: sem servidor HTTP ainda. Pontuação manual via D-pad do Fire TV:
 *   LEFT  = +A    RIGHT = +B    UP = toggle saque
 *   ENTER = confirma fechamento de set
 *   BACK  = rejeita fechamento de set
 *   N     = nova partida
 * Permite testar UI antes da etapa do servidor.
 */
public class PlacarActivity extends AppCompatActivity implements GameState.Listener {

    private TextView clockText;
    private TextView teamANameView, teamBNameView;
    private TextView teamAPointsView, teamBPointsView;
    private TextView teamASetsView, teamBSetsView;
    private View serveDotA, serveDotB;
    private View serveBarBottomA, serveBarBottomB;
    private TextView historyText;
    private TextView setTimer;
    private TextView serverInfo;
    private TextView modeIndicator;
    private LinearLayout winnerOverlay;
    private TextView winnerName;

    private GameStateRepository repo;

    private final Handler tickHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    /** Atualiza relógio + cronômetro a cada 1s. */
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockAndTimer();
            tickHandler.postDelayed(this, 1000L);
        }
    };

    private String appliedTheme = "dark";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // carrega estado antes do super pra aplicar tema correto
        repo = new GameStateRepository(this);
        repo.load();
        appliedTheme = GameState.getInstance().getTheme();
        AppCompatDelegate.setDefaultNightMode(
                "light".equals(appliedTheme)
                        ? AppCompatDelegate.MODE_NIGHT_NO
                        : AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_placar);

        bindViews();

        startPlacarService();
        serverInfo.setText("Controle: " + PlacarService.getCurrentUrl(this));

        render(GameState.getInstance());
    }

    private void startPlacarService() {
        Intent intent = new Intent(this, PlacarService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        GameState.getInstance().addListener(this);
        render(GameState.getInstance());
        tickHandler.post(tickRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        GameState.getInstance().removeListener(this);
        tickHandler.removeCallbacks(tickRunnable);
    }

    private void updateClockAndTimer() {
        clockText.setText(clockFormat.format(new Date()));

        GameState s = GameState.getInstance();
        long elapsedMs = System.currentTimeMillis() - s.getCurrentSetStartTime();
        if (elapsedMs < 0) elapsedMs = 0;
        // cap em 99:59 — set vôlei real raramente passa de ~30min
        long totalSec = elapsedMs / 1000L;
        if (totalSec > 99L * 60L + 59L) totalSec = 99L * 60L + 59L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        setTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
    }

    private void bindViews() {
        clockText = findViewById(R.id.clockText);
        teamANameView = findViewById(R.id.teamAName);
        teamBNameView = findViewById(R.id.teamBName);
        teamAPointsView = findViewById(R.id.teamAPoints);
        teamBPointsView = findViewById(R.id.teamBPoints);
        teamASetsView = findViewById(R.id.teamASets);
        teamBSetsView = findViewById(R.id.teamBSets);
        serveDotA = findViewById(R.id.serveDotA);
        serveDotB = findViewById(R.id.serveDotB);
        serveBarBottomA = findViewById(R.id.serveBarBottomA);
        serveBarBottomB = findViewById(R.id.serveBarBottomB);
        historyText = findViewById(R.id.historyText);
        setTimer = findViewById(R.id.setTimer);
        serverInfo = findViewById(R.id.serverInfo);
        modeIndicator = findViewById(R.id.modeIndicator);
        winnerOverlay = findViewById(R.id.winnerOverlay);
        winnerName = findViewById(R.id.winnerName);
    }

    // ---------- Callback do GameState ----------
    @Override
    public void onStateChanged(final GameState state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                repo.save(state);
                // troca de tema → recreate Activity
                String t = state.getTheme();
                if (!t.equals(appliedTheme)) {
                    appliedTheme = t;
                    AppCompatDelegate.setDefaultNightMode(
                            "light".equals(t)
                                    ? AppCompatDelegate.MODE_NIGHT_NO
                                    : AppCompatDelegate.MODE_NIGHT_YES);
                    recreate();
                    return;
                }
                render(state);
            }
        });
    }

    // ---------- Render ----------
    private void render(GameState s) {
        teamANameView.setText(s.getTeamAName());
        teamBNameView.setText(s.getTeamBName());
        teamAPointsView.setText(String.valueOf(s.getTeamAPoints()));
        teamBPointsView.setText(String.valueOf(s.getTeamBPoints()));
        teamASetsView.setText(String.valueOf(s.getTeamASets()));
        teamBSetsView.setText(String.valueOf(s.getTeamBSets()));

        int activeBg = R.drawable.bg_serve_bar_active;
        int idleBg = R.drawable.bg_serve_bar_idle;
        boolean aServ = s.isTeamAServing();
        serveDotA.setBackgroundResource(aServ ? activeBg : idleBg);
        serveBarBottomA.setBackgroundResource(aServ ? activeBg : idleBg);
        serveDotB.setBackgroundResource(aServ ? idleBg : activeBg);
        serveBarBottomB.setBackgroundResource(aServ ? idleBg : activeBg);

        historyText.setText(formatHistory(s.getHistory()));
        modeIndicator.setText(formatMode(s.getScoringMode()));

        if (s.isMatchFinished()) {
            winnerOverlay.setVisibility(View.VISIBLE);
            winnerName.setText(s.getWinner() != null ? s.getWinner() : "?");
        } else {
            winnerOverlay.setVisibility(View.GONE);
        }
    }

    private String formatHistory(List<SetResult> hist) {
        if (hist == null || hist.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hist.size(); i++) {
            SetResult r = hist.get(i);
            if (i > 0) sb.append("   ·   ");
            sb.append("SET ").append(i + 1).append("  ")
              .append(r.getTeamAPoints()).append("–").append(r.getTeamBPoints());
        }
        return sb.toString();
    }

    private String formatMode(ScoringMode mode) {
        if (mode == null) return "FIVB";
        switch (mode) {
            case ALL_25: return "ALL 25";
            case ALL_15: return "ALL 15";
            case FIVB_DEFAULT:
            default: return "FIVB";
        }
    }

    // ---------- Debug: D-pad/teclado do emulator ----------
    // Remover na etapa 5 quando o servidor HTTP estiver no ar.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        GameState g = GameState.getInstance();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                g.addPointA();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_B:
                g.addPointB();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_S:
                g.toggleServer();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (g.isPendingSetClose()) g.confirmSetClose();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (g.isPendingSetClose()) {
                    g.rejectSetClose();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_N:
                g.newMatch();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
