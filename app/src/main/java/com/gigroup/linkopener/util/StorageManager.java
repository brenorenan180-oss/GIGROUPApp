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

/**
 * StorageManager — equivalente ao chrome.storage.local do background.js.
 * Persiste: enabled, totalOpened, dailyCount, dailyDate, dailyLimit,
 *           allowedDays, openedLinks (Set), cpf, dataNascimento, actionLog.
 */
public class StorageManager {

    private static final String PREFS_NAME    = "gigroup_prefs";
    private static final String KEY_ENABLED   = "enabled";
    private static final String KEY_TOTAL     = "totalOpened";
    private static final String KEY_DAILY_CNT = "dailyCount";
    private static final String KEY_DAILY_DATE= "dailyDate";
    private static final String KEY_DAILY_LIM = "dailyLimit";
    private static final String KEY_ALLOWED   = "allowedDays";
    private static final String KEY_LINKS     = "openedLinks";
    private static final String KEY_LOG       = "actionLog";
    private static final String KEY_CPF       = "cpf";
    private static final String KEY_NASC      = "dataNascimento";

    private static final int    DEFAULT_LIMIT  = 50;
    private static final String DEFAULT_DAYS   = "1,2,3,4,5"; // Seg–Sex
    private static final int    MAX_LOG        = 100;
    private static final int    MAX_LINKS      = 300;

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public StorageManager(Context ctx) {
        prefs  = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // ── Enabled ────────────────────────────────────────────────────────────
    public boolean isEnabled()           { return prefs.getBoolean(KEY_ENABLED, true); }
    public void    setEnabled(boolean v) { editor.putBoolean(KEY_ENABLED, v).apply(); }

    // ── CPF / Data Nascimento ──────────────────────────────────────────────
    public String getCpf()             { return prefs.getString(KEY_CPF, ""); }
    public String getDataNascimento()  { return prefs.getString(KEY_NASC, ""); }
    public void saveCpf(String v)      { editor.putString(KEY_CPF, v).apply(); }
    public void saveDataNascimento(String v) { editor.putString(KEY_NASC, v).apply(); }

    // ── Totais ─────────────────────────────────────────────────────────────
    public int  getTotalOpened()  { return prefs.getInt(KEY_TOTAL, 0); }
    public int  getDailyCount()   { return prefs.getInt(KEY_DAILY_CNT, 0); }
    public int  getDailyLimit()   { return prefs.getInt(KEY_DAILY_LIM, DEFAULT_LIMIT); }
    public void setDailyLimit(int v) { editor.putInt(KEY_DAILY_LIM, v).apply(); }

    public String getDailyDate()  { return prefs.getString(KEY_DAILY_DATE, ""); }

    // ── Dias permitidos ────────────────────────────────────────────────────
    public String getAllowedDaysRaw() { return prefs.getString(KEY_ALLOWED, DEFAULT_DAYS); }
    public void   setAllowedDays(String csv) { editor.putString(KEY_ALLOWED, csv).apply(); }

    public boolean isTodayAllowed() {
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1; // 0=Dom…6=Sáb
        String raw = getAllowedDaysRaw();
        for (String s : raw.split(",")) {
            try { if (Integer.parseInt(s.trim()) == dow) return true; } catch (Exception ignore) {}
        }
        return false;
    }

    // ── Limite diário ──────────────────────────────────────────────────────
    public boolean isDailyLimitReached() {
        String today = todayStr();
        int count = today.equals(getDailyDate()) ? getDailyCount() : 0;
        return count >= getDailyLimit();
    }

    // ── Links abertos ──────────────────────────────────────────────────────
    public boolean isLinkOpened(String url) {
        Set<String> links = prefs.getStringSet(KEY_LINKS, new HashSet<>());
        return links.contains(url);
    }

    public void recordLinkOpened(String url, String snippet) {
        String today = todayStr();

        // Conta diária
        int dailyCount = today.equals(getDailyDate()) ? getDailyCount() : 0;
        dailyCount++;

        // Total
        int total = getTotalOpened() + 1;

        // Set de links
        Set<String> links = new HashSet<>(prefs.getStringSet(KEY_LINKS, new HashSet<>()));
        links.add(url);
        // Limita tamanho (não há removeFirst em Set, mantemos simples)
        if (links.size() > MAX_LINKS) {
            // Remove um elemento qualquer
            links.remove(links.iterator().next());
        }

        // Log de ações
        saveLogEntry(url, snippet);

        editor.putInt(KEY_TOTAL, total)
              .putInt(KEY_DAILY_CNT, dailyCount)
              .putString(KEY_DAILY_DATE, today)
              .putStringSet(KEY_LINKS, links)
              .apply();
    }

    private void saveLogEntry(String url, String snippet) {
        try {
            String raw = prefs.getString(KEY_LOG, "[]");
            JSONArray log = new JSONArray(raw);

            JSONObject entry = new JSONObject();
            entry.put("ts",      new Date().toString());
            entry.put("url",     url);
            entry.put("snippet", snippet);

            // Insere no início
            JSONArray newLog = new JSONArray();
            newLog.put(entry);
            for (int i = 0; i < Math.min(log.length(), MAX_LOG - 1); i++) {
                newLog.put(log.get(i));
            }

            editor.putString(KEY_LOG, newLog.toString()).apply();
        } catch (Exception ignore) {}
    }

    public String getActionLogJson() {
        return prefs.getString(KEY_LOG, "[]");
    }

    public void clearHistory() {
        editor.putInt(KEY_TOTAL, 0)
              .putInt(KEY_DAILY_CNT, 0)
              .putString(KEY_DAILY_DATE, "")
              .putStringSet(KEY_LINKS, new HashSet<>())
              .putString(KEY_LOG, "[]")
              .apply();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
