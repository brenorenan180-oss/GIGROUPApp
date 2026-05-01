package com.gigroup.linkopener.service;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.gigroup.linkopener.*;
import com.gigroup.linkopener.ui.MainActivity;
import java.util.concurrent.*;
public class AutoOpenService extends Service {
    private static final String TAG="GIGROUP-SVC";
    private static final int NOTIF_ID=1001;
    private PowerManager.WakeLock wl;
    private ScheduledExecutorService sched;
    @Override public int onStartCommand(Intent i,int f,int s){
        startForeground(NOTIF_ID,buildNotif());acquireWL();startCheck();return START_STICKY;
    }
    private void acquireWL(){
        if(wl!=null&&wl.isHeld())return;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wl=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GIGROUPMonitor::WL");
        wl.acquire();Log.i(TAG,"WakeLock adquirido");
    }
    private void startCheck(){
        sched=Executors.newSingleThreadScheduledExecutor();
        sched.scheduleAtFixedRate(()->{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni=cm!=null?cm.getActiveNetworkInfo():null;
            if(ni==null||!ni.isConnected())return;
            if(MainActivity.webView!=null){
                MainActivity.webView.post(()->{
                    String u=MainActivity.webView.getUrl();
                    if(u==null||u.isEmpty()||u.equals("about:blank"))MainActivity.webView.loadUrl("https://web.whatsapp.com/");
                });
            }
        },30,30,TimeUnit.SECONDS);
    }
    private Notification buildNotif(){
        Intent i=new Intent(this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,GIGROUPApp.CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("GIGROUP Monitor")
            .setContentText("N1-N6 + NLS ativos").setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build();
    }
    @Override public void onDestroy(){if(wl!=null&&wl.isHeld())wl.release();if(sched!=null)sched.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
