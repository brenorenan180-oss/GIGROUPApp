package com.gigroup.linkopener.util;
import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import android.webkit.JavascriptInterface;
import androidx.core.app.NotificationCompat;
import com.gigroup.linkopener.*;
import com.gigroup.linkopener.ui.FormActivity;
import org.json.JSONObject;
public class JsBridge {
    private static final String TAG="GIGROUP-Bridge";
    private static final long SPAM_MS=3000;
    protected final Context ctx;
    protected final StorageManager storage;
    protected final Handler main=new Handler(Looper.getMainLooper());
    private long lastAction=0;
    public JsBridge(Context c,StorageManager s){ctx=c;storage=s;}
    public void onFormPreenchido(){}
    @JavascriptInterface public void postMessage(String json){
        try{JSONObject msg=new JSONObject(json);String type=msg.optString("type","");
            switch(type){
                case"OPEN_LINK":handleOpenLink(msg);break;
                case"FORM_PREENCHIDO":Log.i(TAG,"Formulário: "+msg.optString("vagaId"));main.post(this::onFormPreenchido);break;
            }
        }catch(Exception e){Log.e(TAG,"postMessage: "+e.getMessage());}
    }
    @JavascriptInterface public String getStateJson(){
        try{JSONObject o=new JSONObject();o.put("enabled",storage.isEnabled());o.put("totalOpened",storage.getTotalOpened());
            o.put("dailyCount",storage.getDailyCount());o.put("dailyDate",storage.getDailyDate());o.put("dailyLimit",storage.getDailyLimit());return o.toString();}
        catch(Exception e){return"{}";}
    }
    @JavascriptInterface public String getConfigJson(){
        try{JSONObject cfg=new JSONObject();cfg.put("cpf",storage.getCpf());cfg.put("dataNascimento",storage.getDataNascimento());
            JSONObject w=new JSONObject();w.put("config",cfg);return w.toString();}catch(Exception e){return"{}";}
    }
    @JavascriptInterface public void setEnabled(boolean v){storage.setEnabled(v);}
    private void handleOpenLink(JSONObject msg){
        try{String url=msg.optString("url",""),snippet=msg.optString("messageSnippet","");
            if(url.isEmpty()||!storage.isEnabled())return;
            long now=System.currentTimeMillis();
            if(now-lastAction<SPAM_MS)return;
            if(storage.isLinkOpened(url)){Log.d(TAG,"Dup");return;}
            if(storage.isDailyLimitReached()){Log.d(TAG,"Limite");return;}
            if(!storage.isTodayAllowed()){Log.d(TAG,"Dia");return;}
            lastAction=now;storage.recordLinkOpened(url,snippet);
            showNotif(url,snippet);
            main.post(()->{Intent i=new Intent(ctx,FormActivity.class);i.putExtra("url",url);i.putExtra("snippet",snippet);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);});
        }catch(Exception e){Log.e(TAG,"openLink: "+e.getMessage());}
    }
    private void showNotif(String url,String snippet){
        try{Intent i=new Intent(ctx,FormActivity.class);i.putExtra("url",url);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi=PendingIntent.getActivity(ctx,(int)System.currentTimeMillis(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b=new NotificationCompat.Builder(ctx,GIGROUPApp.CHANNEL_LINKS)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("🔗 Nova vaga!")
                .setContentText(snippet.isEmpty()?url:snippet).setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH);
            ((NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify((int)System.currentTimeMillis(),b.build());
        }catch(Exception e){Log.e(TAG,"notif: "+e.getMessage());}
    }
}
