package com.mauricio.placarvolei;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Servidor HTTP embutido que expõe o GameState para o celular controlador.
 *
 * Endpoints:
 *   GET  /                    → HTML do controle (etapa 6, agora 404 placeholder)
 *   GET  /style.css           → CSS
 *   GET  /script.js           → JS
 *   GET  /state               → GameState atual em JSON
 *   POST /score/A/plus        → +1 A
 *   POST /score/A/minus       → -1 A
 *   POST /score/B/plus        → +1 B
 *   POST /score/B/minus       → -1 B
 *   POST /set/confirm-close   → confirma fim de set
 *   POST /set/reject-close    → rejeita fim de set
 *   POST /serve/toggle        → troca saque
 *   POST /teams/rename        → body {"teamA":"X","teamB":"Y"}
 *   POST /match/best-of       → body {"bestOf":5}
 *   POST /match/scoring-mode  → body {"mode":"FIVB_DEFAULT"}
 *   POST /match/new           → nova partida
 *   POST /match/reset         → reset total
 *
 * Todas mutações retornam JSON com estado atualizado.
 */
public class PlacarServer extends NanoHTTPD {

    private static final String TAG = "PlacarServer";
    public static final int PORT = 8080;

    /** PIN fixo. Exigido em todas as mutações (POST). GETs ficam livres.
     *  IMPORTANTE: troque este valor antes de instalar em produção. */
    public static final String PIN = "1234";

    private final Context context;
    private final Gson gson = new Gson();

    public PlacarServer(Context ctx) {
        super(PORT);
        this.context = ctx.getApplicationContext();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        try {
            // ----- Arquivos estáticos (etapa 6 implementa de verdade) -----
            if (Method.GET.equals(method)) {
                if ("/".equals(uri) || "/index.html".equals(uri)) {
                    return serveAsset("index.html", "text/html");
                }
                if ("/style.css".equals(uri)) {
                    return serveAsset("style.css", "text/css");
                }
                if ("/script.js".equals(uri)) {
                    return serveAsset("script.js", "application/javascript");
                }
                if ("/state".equals(uri)) {
                    return jsonOk(GameState.getInstance());
                }
                if ("/favicon.ico".equals(uri)) {
                    return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "");
                }
            }

            // ----- Endpoints de mutação (POST) -----
            if (Method.POST.equals(method)) {
                if (!isPinValid(session)) {
                    return jsonError(Response.Status.UNAUTHORIZED, "PIN inválido");
                }
                GameState g = GameState.getInstance();

                switch (uri) {
                    case "/score/A/plus":   g.addPointA(); return jsonOk(g);
                    case "/score/A/minus":  g.subtractPointA(); return jsonOk(g);
                    case "/score/B/plus":   g.addPointB(); return jsonOk(g);
                    case "/score/B/minus":  g.subtractPointB(); return jsonOk(g);
                    case "/set/confirm-close": g.confirmSetClose(); return jsonOk(g);
                    case "/set/reject-close":  g.rejectSetClose(); return jsonOk(g);
                    case "/serve/toggle":   g.toggleServer(); return jsonOk(g);
                    case "/match/new":      g.newMatch(); return jsonOk(g);
                    case "/match/reset":    g.resetMatch(); return jsonOk(g);
                    case "/theme": {
                        JsonObject body = readJsonBody(session);
                        if (body.has("theme")) g.setTheme(body.get("theme").getAsString());
                        return jsonOk(g);
                    }
                    case "/teams/rename": {
                        JsonObject body = readJsonBody(session);
                        String a = body.has("teamA") ? body.get("teamA").getAsString() : null;
                        String b = body.has("teamB") ? body.get("teamB").getAsString() : null;
                        g.renameTeam(a, b);
                        return jsonOk(g);
                    }
                    case "/match/best-of": {
                        JsonObject body = readJsonBody(session);
                        if (body.has("bestOf")) g.setBestOf(body.get("bestOf").getAsInt());
                        return jsonOk(g);
                    }
                    case "/match/scoring-mode": {
                        JsonObject body = readJsonBody(session);
                        if (body.has("mode")) {
                            String m = body.get("mode").getAsString();
                            try {
                                g.setScoringMode(ScoringMode.valueOf(m));
                            } catch (IllegalArgumentException e) {
                                return jsonError(Response.Status.BAD_REQUEST, "modo inválido: " + m);
                            }
                        }
                        return jsonOk(g);
                    }
                }
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain",
                    "Not Found: " + method + " " + uri);

        } catch (Exception e) {
            Log.e(TAG, "Erro processando " + method + " " + uri, e);
            return jsonError(Response.Status.INTERNAL_ERROR, e.getMessage());
        }
    }

    // ---------- Auth ----------
    /** Aceita PIN via header X-Pin ou query param ?pin=1579. */
    private boolean isPinValid(IHTTPSession session) {
        String header = session.getHeaders().get("x-pin"); // NanoHTTPD lowercase
        if (PIN.equals(header)) return true;
        String query = session.getParms().get("pin");
        return PIN.equals(query);
    }

    // ---------- Helpers ----------
    private Response jsonOk(GameState g) {
        Response r = newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(g));
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, X-Pin");
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private Response jsonError(Response.Status status, String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", msg != null ? msg : "");
        Response r = newFixedLengthResponse(status, "application/json", gson.toJson(obj));
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private JsonObject readJsonBody(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files); // NanoHTTPD escreve body em "postData"
        String json = files.get("postData");
        if (json == null || json.isEmpty()) return new JsonObject();
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private Response serveAsset(String filename, String mime) {
        try {
            InputStream is = context.getAssets().open(filename);
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            Response r = newFixedLengthResponse(Response.Status.OK, mime, sb.toString());
            r.addHeader("Cache-Control", "no-store");
            return r;
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain",
                    "asset não encontrado: " + filename);
        }
    }
}
