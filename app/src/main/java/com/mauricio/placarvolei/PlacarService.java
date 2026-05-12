package com.mauricio.placarvolei;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.mauricio.placarvolei.util.IpUtils;

/**
 * Foreground Service que hospeda o {@link PlacarServer}.
 *
 * Foreground porque:
 *  - Fire TV pode matar services background quando Activity sai do foco.
 *  - Sem isso, o servidor cai se o usuário sair do app por engano.
 *
 * Mostra notificação obrigatória com IP+porta — também útil pra debug.
 *
 * Ciclo:
 *  - onCreate: cria server, startForeground com notification.
 *  - onStartCommand: ignora intent, retorna START_STICKY (reinicia se sistema matar).
 *  - onDestroy: para server.
 */
public class PlacarService extends Service {

    private static final String TAG = "PlacarService";
    private static final String CHANNEL_ID = "placar_server_channel";
    private static final int NOTIF_ID = 1001;

    private PlacarServer server;
    private String currentUrl = "http://0.0.0.0:" + PlacarServer.PORT;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        String ip = IpUtils.getLocalIpv4();
        currentUrl = "http://" + ip + ":" + PlacarServer.PORT;

        startForeground(NOTIF_ID, buildNotification(currentUrl));

        server = new PlacarServer(this);
        try {
            server.start(0, false); // timeout=0, daemon=false → roda em thread normal
            Log.i(TAG, "Servidor iniciado em " + currentUrl);
        } catch (Exception e) {
            Log.e(TAG, "Falha ao iniciar servidor", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            Log.i(TAG, "Servidor parado");
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static String getCurrentUrl(android.content.Context ctx) {
        // helper estático pra Activity exibir antes do service subir
        return "http://" + IpUtils.getLocalIpv4() + ":" + PlacarServer.PORT;
    }

    // ---------- Notification ----------
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Servidor do Placar",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Mantém o servidor HTTP do placar rodando");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String url) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Placar de Vôlei")
                .setContentText("Controle: " + url)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
