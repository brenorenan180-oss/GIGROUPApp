package com.gigroup.linkopener.service;
import android.content.*;
import android.os.Build;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        Intent s=new Intent(c,AutoOpenService.class);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)c.startForegroundService(s);else c.startService(s);
    }
}
