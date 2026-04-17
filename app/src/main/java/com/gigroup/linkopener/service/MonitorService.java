package com.gigroup.linkopener.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.gigroup.linkopener.GIGROUPApp;
import com.gigroup.linkopener.R;
import com.gigroup.linkopener.ui.MainActivity;

/**
 * MonitorService — serviço em foreground que mantém o processo vivo
 * enquanto o usuário está com outra app, garantindo que o WebView
 * continue funcionando e detectando mensagens.
 */
public class MonitorService extends Service {

    private static final int NOTIF_ID = 1001;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        return START_STICKY; // reinicia automaticamente se morrer
    }

    private Notification buildNotification() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, GIGROUPApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_link)
            .setContentTitle("GIGROUP Monitor ativo")
            .setContentText("Monitorando grupo GIGROUP SIMULAÇÃO")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // não é um bound service
    }
}
