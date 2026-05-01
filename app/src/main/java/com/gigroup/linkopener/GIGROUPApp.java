package com.gigroup.linkopener;
import android.app.*;
import android.os.Build;
public class GIGROUPApp extends Application {
    public static final String CHANNEL_MONITOR = "gigroup_monitor";
    public static final String CHANNEL_LINKS   = "gigroup_links";
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel m = new NotificationChannel(CHANNEL_MONITOR,"Monitor GIGROUP",NotificationManager.IMPORTANCE_LOW);
            m.setShowBadge(false); nm.createNotificationChannel(m);
            NotificationChannel l = new NotificationChannel(CHANNEL_LINKS,"Links Detectados",NotificationManager.IMPORTANCE_HIGH);
            l.enableVibration(true); nm.createNotificationChannel(l);
        }
    }
}
