package com.mauricio.placarvolei;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;

/**
 * Persiste {@link GameState} em SharedPreferences via Gson.
 *
 * Estratégia:
 *  - save(state): serializa estado completo em JSON e grava no SharedPreferences.
 *  - load(): lê JSON, desserializa pra um GameState temporário, copia campos pro
 *    singleton existente via {@link GameState#restoreFrom}. Preserva listeners.
 *  - clear(): apaga estado salvo (volta a defaults no próximo load).
 *
 * Chamado pela Activity em onCreate (load) e pelo Service em cada mutação (save).
 */
public class GameStateRepository {

    private static final String TAG = "GameStateRepo";
    private static final String PREFS_NAME = "placar_volei_prefs";
    private static final String KEY_STATE = "state_json";

    private final SharedPreferences prefs;
    private final Gson gson;

    public GameStateRepository(Context ctx) {
        // applicationContext evita memory leak (não segura referência de Activity)
        this.prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    /** Serializa estado e grava no disco. Síncrono (apply() async não é garantido). */
    public void save(GameState state) {
        try {
            String json = gson.toJson(state);
            prefs.edit().putString(KEY_STATE, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Falha ao salvar GameState", e);
        }
    }

    /**
     * Carrega estado do disco e aplica no singleton.
     * Se não houver estado salvo ou JSON corrompido, mantém defaults.
     * @return true se carregou, false se não havia estado ou falhou.
     */
    public boolean load() {
        String json = prefs.getString(KEY_STATE, null);
        if (json == null) return false;
        try {
            GameState snapshot = gson.fromJson(json, GameState.class);
            if (snapshot != null) {
                GameState.getInstance().restoreFrom(snapshot);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Falha ao desserializar GameState; ignorando", e);
        }
        return false;
    }

    /** Apaga estado salvo. */
    public void clear() {
        prefs.edit().remove(KEY_STATE).apply();
    }
}
