package com.gigroup.linkopener.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.core.app.NotificationCompat;

import com.gigroup.linkopener.GIGROUPApp;
import com.gigroup.linkopener.R;
import com.gigroup.linkopener.ui.FormActivity;
import com.gigroup.linkopener.ui.MainActivity;

import org.json.JSONObject;

public class JsBridge {

    private static final String TAG = "GIGROUP-Bridge";
    private static final long   SPAM_MS = 3000;

    protected final Context        ctx;
    protected final StorageManager storage;
    protected final Handler        main = new Handler(Looper.getMainLooper());
    private long lastAction = 0;

    public JsBridge(Context ctx, StorageManager storage) {
        this.ctx     = ctx;
        this.storage = storage;
    }

    // Sobrescrito pela FormActivity para saber quando fechar
    public void onFormPreenchido() {}

    @JavascriptInterface
    public void postMessage(String json) {
        try {
            JSONObject msg = new JSONObject(json);
            String type = msg.optString("type", "");

            switch (type) {
                case "OPEN_LINK":
                    handleOpenLink(msg);
                    break;
                case "FORM_PREENCHIDO":
                    Log.i(TAG, "📋 Formulário preenchido: " + msg.optString("vagaId"));
                    main.post(this::onFormPreenchido);
                    break;
                case "STORAGE_SET":
                    // Ignora — bridge JS gerencia via sessionStorage
                    break;
                default:
                    Log.d(TAG, "Msg: " + type);
            }
        } catch (Exception e) {
            Log.e(TAG, "postMessage: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public String getStateJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("enabled",      storage.isEnabled());
            o.put("totalOpened",  storage.getTotalOpened());
            o.put("dailyCount",   storage.getDailyCount());
            o.put("dailyDate",    storage.getDailyDate());
            o.put("dailyLimit",   storage.getDailyLimit());
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    @JavascriptInterface
    public String getConfigJson() {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("cpf",            storage.getCpf());
            cfg.put("dataNascimento", storage.getDataNascimento());
            JSONObject w = new JSONObject();
            w.put("config", cfg);
            return w.toString();
        } catch (Exception e) { return "{}"; }
    }

    @JavascriptInterface
    public void setEnabled(boolean enabled) { storage.setEnabled(enabled); }

    @JavascriptInterface
    public void saveConfig(String cpf, String dataNascimento) {
        storage.saveCpf(cpf);
        storage.saveDataNascimento(dataNascimento);
    }

    // ── Abre link de vaga ─────────────────────────────────────────────────
    private void handleOpenLink(JSONObject msg) {
        try {
            String url     = msg.optString("url", "");
            String group   = msg.optString("groupName", "");
            String snippet = msg.optString("messageSnippet", "");

            if (url.isEmpty() || !storage.isEnabled()) return;

            long now = System.currentTimeMillis();
            if (now - lastAction < SPAM_MS) return;
            if (storage.isLinkOpened(url)) { Log.d(TAG, "Duplicata"); return; }
            if (storage.isDailyLimitReached()) { Log.d(TAG, "Limite diário"); return; }
            if (!storage.isTodayAllowed()) { Log.d(TAG, "Dia não permitido"); return; }

            lastAction = now;
            storage.recordLinkOpened(url, snippet);

            showNotification(url, snippet);

            main.post(() -> {
                // Abre FormActivity passando a URL
                Intent i = new Intent(ctx, FormActivity.class);
                i.putExtra("url", url);
                i.putExtra("snippet", snippet);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            });

        } catch (Exception e) {
            Log.e(TAG, "handleOpenLink: " + e.getMessage());
        }
    }

    private void showNotification(String url, String snippet) {
        try {
            Intent i = new Intent(ctx, FormActivity.class);
            i.putExtra("url", url);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pi = PendingIntent.getActivity(
                ctx, (int) System.currentTimeMillis(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, GIGROUPApp.CHANNEL_LINKS)
                .setSmallIcon(R.drawable.ic_link)
                .setContentTitle("🔗 Nova vaga detectada!")
                .setContentText(snippet.isEmpty() ? url : snippet)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(url))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify((int) System.currentTimeMillis(), b.build());

        } catch (Exception e) {
            Log.e(TAG, "notification: " + e.getMessage());
        }
    }
}
