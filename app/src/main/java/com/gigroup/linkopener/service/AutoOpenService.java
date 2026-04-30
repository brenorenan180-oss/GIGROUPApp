package com.gigroup.linkopener.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.gigroup.linkopener.GIGROUPApp;
import com.gigroup.linkopener.R;
import com.gigroup.linkopener.ui.MainActivity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AutoOpenService — serviço foreground que:
 * - Exibe notificação persistente (mantém processo vivo)
 * - Mantém WakeLock parcial (CPU ativa com tela apagada)
 * - Verifica conectividade a cada 30s e recarrega WhatsApp se necessário
 * - Reinicia automaticamente com START_STICKY
 */
public class AutoOpenService extends Service {

    private static final String TAG     = "GIGROUP-Service";
    private static final int    NOTIF_ID = 1001;

    private PowerManager.WakeLock wakeLock;
    private ScheduledExecutorService scheduler;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "AutoOpenService iniciado");
        startForeground(NOTIF_ID, buildNotification());
        acquireWakeLock();
        startConnectivityCheck();
        return START_STICKY; // reinicia automaticamente
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GIGROUPMonitor::WakeLock"
        );
        wakeLock.acquire(); // sem timeout — serviço gerencia ciclo de vida
        Log.i(TAG, "WakeLock adquirido");
    }

    private void startConnectivityCheck() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (!isConnected()) {
                Log.w(TAG, "Sem conexão — aguardando...");
                return;
            }
            // Verifica se o WebView está vivo — recarrega se necessário
            if (MainActivity.webView != null) {
                MainActivity.webView.post(() -> {
                    String url = MainActivity.webView.getUrl();
                    if (url == null || url.isEmpty() || url.equals("about:blank")) {
                        Log.w(TAG, "WebView morto — recarregando WhatsApp Web");
                        MainActivity.webView.loadUrl("https://web.whatsapp.com/");
                    }
                });
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private boolean isConnected() {
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, GIGROUPApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_link)
            .setContentTitle("GIGROUP Monitor ativo")
            .setContentText("Monitorando grupo — N1 a N6 ativos")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.i(TAG, "WakeLock liberado");
        }
        if (scheduler != null) scheduler.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
