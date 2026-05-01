package com.gigroup.linkopener.util;
import android.content.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
public class StorageManager {
    private static final String PREFS="gigroup_prefs",K_EN="enabled",K_TOT="totalOpened",
        K_DC="dailyCount",K_DD="dailyDate",K_DL="dailyLimit",K_DAYS="allowedDays",
        K_LINKS="openedLinks",K_LOG="actionLog",K_CPF="cpf",K_NASC="dataNascimento";
    private final SharedPreferences p;
    public StorageManager(Context c){p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public boolean isEnabled(){return p.getBoolean(K_EN,true);}
    public void setEnabled(boolean v){p.edit().putBoolean(K_EN,v).apply();}
    public String getCpf(){return p.getString(K_CPF,"");}
    public void saveCpf(String v){p.edit().putString(K_CPF,v).apply();}
    public String getDataNascimento(){return p.getString(K_NASC,"");}
    public void saveDataNascimento(String v){p.edit().putString(K_NASC,v).apply();}
    public int getTotalOpened(){return p.getInt(K_TOT,0);}
    public int getDailyCount(){return p.getInt(K_DC,0);}
    public int getDailyLimit(){return p.getInt(K_DL,50);}
    public void setDailyLimit(int v){p.edit().putInt(K_DL,v).apply();}
    public String getDailyDate(){return p.getString(K_DD,"");}
    public String getAllowedDaysRaw(){return p.getString(K_DAYS,"1,2,3,4,5");}
    public void setAllowedDays(String csv){p.edit().putString(K_DAYS,csv).apply();}
    public boolean isTodayAllowed(){
        int dow=Calendar.getInstance().get(Calendar.DAY_OF_WEEK)-1;
        for(String s:getAllowedDaysRaw().split(",")){try{if(Integer.parseInt(s.trim())==dow)return true;}catch(Exception e){}}
        return false;
    }
    public boolean isDailyLimitReached(){
        String t=today();int c=t.equals(getDailyDate())?getDailyCount():0;return c>=getDailyLimit();
    }
    public boolean isLinkOpened(String url){return p.getStringSet(K_LINKS,new HashSet<>()).contains(url);}
    public void recordLinkOpened(String url,String snippet){
        String t=today();int dc=t.equals(getDailyDate())?getDailyCount():0;dc++;
        int tot=getTotalOpened()+1;
        Set<String> links=new HashSet<>(p.getStringSet(K_LINKS,new HashSet<>()));links.add(url);
        try{JSONArray log=new JSONArray(p.getString(K_LOG,"[]"));
            JSONObject e=new JSONObject();e.put("ts",new Date().toString());e.put("url",url);e.put("snippet",snippet);
            JSONArray nl=new JSONArray();nl.put(e);for(int i=0;i<Math.min(log.length(),99);i++)nl.put(log.get(i));
            p.edit().putString(K_LOG,nl.toString()).apply();}catch(Exception ignored){}
        p.edit().putInt(K_TOT,tot).putInt(K_DC,dc).putString(K_DD,t).putStringSet(K_LINKS,links).apply();
    }
    public void clearHistory(){p.edit().putInt(K_TOT,0).putInt(K_DC,0).putString(K_DD,"").putString(K_LOG,"[]").putStringSet(K_LINKS,new HashSet<>()).apply();}
    public String getActionLogJson(){return p.getString(K_LOG,"[]");}
    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
}
