package com.mauricio.placarvolei;

/**
 * Resultado de um set já encerrado.
 * Imutável após criação; serve só pra histórico.
 */
public class SetResult {

    private final int teamAPoints;
    private final int teamBPoints;
    private final String winner; // nome do time vencedor

    public SetResult(int teamAPoints, int teamBPoints, String winner) {
        this.teamAPoints = teamAPoints;
        this.teamBPoints = teamBPoints;
        this.winner = winner;
    }

    public int getTeamAPoints() {
        return teamAPoints;
    }

    public int getTeamBPoints() {
        return teamBPoints;
    }

    public String getWinner() {
        return winner;
    }
}
