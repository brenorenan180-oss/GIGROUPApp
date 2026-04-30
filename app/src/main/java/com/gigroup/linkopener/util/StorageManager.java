package com.gigroup.linkopener.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class StorageManager {

    private static final String PREFS      = "gigroup_prefs";
    private static final String K_ENABLED  = "enabled";
    private static final String K_TOTAL    = "totalOpened";
    private static final String K_DCNT     = "dailyCount";
    private static final String K_DDATE    = "dailyDate";
    private static final String K_DLIM     = "dailyLimit";
    private static final String K_DAYS     = "allowedDays";
    private static final String K_LINKS    = "openedLinks";
    private static final String K_LOG      = "actionLog";
    private static final String K_CPF      = "cpf";
    private static final String K_NASC     = "dataNascimento";

    private final SharedPreferences prefs;

    public StorageManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isEnabled()           { return prefs.getBoolean(K_ENABLED, true); }
    public void    setEnabled(boolean v) { prefs.edit().putBoolean(K_ENABLED, v).apply(); }

    public String getCpf()                  { return prefs.getString(K_CPF, ""); }
    public void   saveCpf(String v)         { prefs.edit().putString(K_CPF, v).apply(); }
    public String getDataNascimento()       { return prefs.getString(K_NASC, ""); }
    public void   saveDataNascimento(String v) { prefs.edit().putString(K_NASC, v).apply(); }

    public int  getTotalOpened()  { return prefs.getInt(K_TOTAL, 0); }
    public int  getDailyCount()   { return prefs.getInt(K_DCNT,  0); }
    public int  getDailyLimit()   { return prefs.getInt(K_DLIM,  50); }
    public void setDailyLimit(int v) { prefs.edit().putInt(K_DLIM, v).apply(); }
    public String getDailyDate()  { return prefs.getString(K_DDATE, ""); }

    public String getAllowedDaysRaw()        { return prefs.getString(K_DAYS, "1,2,3,4,5"); }
    public void   setAllowedDays(String csv) { prefs.edit().putString(K_DAYS, csv).apply(); }

    public boolean isTodayAllowed() {
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        for (String s : getAllowedDaysRaw().split(",")) {
            try { if (Integer.parseInt(s.trim()) == dow) return true; } catch (Exception ignored) {}
        }
        return false;
    }

    public boolean isDailyLimitReached() {
        String today = today();
        int cnt = today.equals(getDailyDate()) ? getDailyCount() : 0;
        return cnt >= getDailyLimit();
    }

    public boolean isLinkOpened(String url) {
        return prefs.getStringSet(K_LINKS, new HashSet<>()).contains(url);
    }

    public void recordLinkOpened(String url, String snippet) {
        String today = today();
        int daily = today.equals(getDailyDate()) ? getDailyCount() : 0;
        daily++;
        int total = getTotalOpened() + 1;

        Set<String> links = new HashSet<>(prefs.getStringSet(K_LINKS, new HashSet<>()));
        links.add(url);

        try {
            JSONArray log = new JSONArray(prefs.getString(K_LOG, "[]"));
            JSONObject e = new JSONObject();
            e.put("ts", new Date().toString());
            e.put("url", url);
            e.put("snippet", snippet);
            JSONArray newLog = new JSONArray();
            newLog.put(e);
            for (int i = 0; i < Math.min(log.length(), 99); i++) newLog.put(log.get(i));
            prefs.edit().putString(K_LOG, newLog.toString()).apply();
        } catch (Exception ignored) {}

        prefs.edit()
            .putInt(K_TOTAL, total)
            .putInt(K_DCNT, daily)
            .putString(K_DDATE, today)
            .putStringSet(K_LINKS, links)
            .apply();
    }

    public void clearHistory() {
        prefs.edit()
            .putInt(K_TOTAL, 0).putInt(K_DCNT, 0)
            .putString(K_DDATE, "").putString(K_LOG, "[]")
            .putStringSet(K_LINKS, new HashSet<>())
            .apply();
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
