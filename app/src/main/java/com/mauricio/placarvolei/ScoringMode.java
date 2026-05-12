package com.mauricio.placarvolei;

/**
 * Modo de pontuação da partida.
 *
 * - FIVB_DEFAULT: sets normais vão até 25, set decisivo (último possível) vai até 15.
 * - ALL_25: todos os sets vão até 25, inclusive o decisivo.
 * - ALL_15: todos os sets vão até 15.
 *
 * Em qualquer modo, vence o set quem atingir o limite COM diferença mínima de 2 pontos.
 */
public enum ScoringMode {
    FIVB_DEFAULT,
    ALL_25,
    ALL_15
}
