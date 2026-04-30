package com.gigroup.linkopener;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class GIGROUPApp extends Application {

    public static final String CHANNEL_MONITOR = "gigroup_monitor";
    public static final String CHANNEL_LINKS   = "gigroup_links";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel monitor = new NotificationChannel(
                CHANNEL_MONITOR, "Monitor GIGROUP", NotificationManager.IMPORTANCE_LOW);
            monitor.setDescription("Mantém o monitoramento ativo em segundo plano");
            monitor.setShowBadge(false);
            nm.createNotificationChannel(monitor);

            NotificationChannel links = new NotificationChannel(
                CHANNEL_LINKS, "Links Detectados", NotificationManager.IMPORTANCE_HIGH);
            links.setDescription("Notifica quando um link de vaga é encontrado");
            links.enableVibration(true);
            nm.createNotificationChannel(links);
        }
    }
}
