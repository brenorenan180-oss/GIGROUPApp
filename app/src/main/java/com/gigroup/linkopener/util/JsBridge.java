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
import com.gigroup.linkopener.ui.LinkHandlerActivity;
import com.gigroup.linkopener.ui.MainActivity;

import org.json.JSONObject;

/**
 * JsBridge — substitui chrome.runtime.sendMessage no contexto Android.
 *
 * O android_bridge.js mapeia chrome.runtime.sendMessage → AndroidBridge.postMessage(json)
 * e chrome.storage.local → AndroidBridge.storageGet / storageSet.
 *
 * Todos os métodos @JavascriptInterface são chamados numa thread de background;
 * operações de UI usam Handler(Looper.getMainLooper()).
 */
public class JsBridge {

    private static final String TAG = "GIGROUP-Bridge";

    private final Context        ctx;
    private final StorageManager storage;
    private final Handler        mainHandler = new Handler(Looper.getMainLooper());

    // Anti-spam: igual ao background.js
    private static final long SPAM_INTERVAL_MS = 3000;
    private long lastActionAt = 0;

    public JsBridge(Context ctx, StorageManager storage) {
        this.ctx     = ctx;
        this.storage = storage;
    }

    // ── Chamado pelo JS quando detecta um link de vaga ─────────────────────
    @JavascriptInterface
    public void postMessage(String json) {
        try {
            JSONObject msg = new JSONObject(json);
            String type = msg.optString("type", "");

            switch (type) {
                case "OPEN_LINK":
                    handleOpenLink(msg);
                    break;
                case "GET_STATE":
                    // Retorna via callback JS — não bloqueia
                    break;
                case "FORM_PREENCHIDO":
                    Log.i(TAG, "Formulário preenchido: " + msg.optString("vagaId"));
                    break;
                default:
                    Log.d(TAG, "Mensagem desconhecida: " + type);
            }
        } catch (Exception e) {
            Log.e(TAG, "postMessage error: " + e.getMessage());
        }
    }

    // ── Retorna estado atual como JSON string ──────────────────────────────
    @JavascriptInterface
    public String getStateJson() {
        try {
            JSONObject state = new JSONObject();
            state.put("enabled",      storage.isEnabled());
            state.put("totalOpened",  storage.getTotalOpened());
            state.put("dailyCount",   storage.getDailyCount());
            state.put("dailyDate",    storage.getDailyDate());
            state.put("dailyLimit",   storage.getDailyLimit());
            return state.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ── Retorna config (CPF, data nascimento) como JSON ───────────────────
    @JavascriptInterface
    public String getConfigJson() {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("cpf",            storage.getCpf());
            cfg.put("dataNascimento", storage.getDataNascimento());
            JSONObject wrapper = new JSONObject();
            wrapper.put("config", cfg);
            return wrapper.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ── Salva config vinda do JS ──────────────────────────────────────────
    @JavascriptInterface
    public void saveConfig(String cpf, String dataNascimento) {
        storage.saveCpf(cpf);
        storage.saveDataNascimento(dataNascimento);
        Log.i(TAG, "Config salva: CPF=" + cpf);
    }

    // ── Ativa / desativa automação ─────────────────────────────────────────
    @JavascriptInterface
    public void setEnabled(boolean enabled) {
        storage.setEnabled(enabled);
        Log.i(TAG, "Automação: " + (enabled ? "ATIVA" : "PAUSADA"));
    }

    // ────────────────────────────────────────────────────────────────────────
    // OPEN LINK — lógica equivalente ao background.js handleOpenLink
    // ────────────────────────────────────────────────────────────────────────
    private void handleOpenLink(JSONObject msg) {
        try {
            String url         = msg.optString("url", "");
            String groupName   = msg.optString("groupName", "");
            String snippet     = msg.optString("messageSnippet", "");

            if (url.isEmpty()) return;
            if (!storage.isEnabled()) {
                Log.d(TAG, "Automação desativada — ignorado");
                return;
            }

            // ── Anti-spam ─────────────────────────────────────────────────
            long now = System.currentTimeMillis();
            if (now - lastActionAt < SPAM_INTERVAL_MS) {
                Log.d(TAG, "Anti-spam — ignorado");
                return;
            }

            // ── Deduplicação ──────────────────────────────────────────────
            if (storage.isLinkOpened(url)) {
                Log.d(TAG, "Duplicata — ignorado: " + url);
                return;
            }

            // ── Limite diário ─────────────────────────────────────────────
            if (storage.isDailyLimitReached()) {
                Log.d(TAG, "Limite diário atingido");
                return;
            }

            // ── Dia da semana ─────────────────────────────────────────────
            if (!storage.isTodayAllowed()) {
                Log.d(TAG, "Dia não permitido");
                return;
            }

            // ── Registra e abre ───────────────────────────────────────────
            lastActionAt = now;
            storage.recordLinkOpened(url, snippet);

            Log.i(TAG, "✅ Abrindo: " + url);

            // Notificação de link encontrado
            showLinkNotification(url, snippet);

            // Abre o link na LinkHandlerActivity (nova WebView com form.js)
            mainHandler.post(() -> {
                Intent i = new Intent(ctx, LinkHandlerActivity.class);
                i.putExtra("url", url);
                i.putExtra("snippet", snippet);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            });

        } catch (Exception e) {
            Log.e(TAG, "handleOpenLink error: " + e.getMessage());
        }
    }

    // ── Notificação quando link é detectado ───────────────────────────────
    private void showLinkNotification(String url, String snippet) {
        try {
            Intent intent = new Intent(ctx, LinkHandlerActivity.class);
            intent.putExtra("url", url);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pi = PendingIntent.getActivity(
                ctx, (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                ctx, GIGROUPApp.CHANNEL_LINKS)
                .setSmallIcon(R.drawable.ic_link)
                .setContentTitle("🔗 Link de vaga detectado!")
                .setContentText(snippet.isEmpty() ? url : snippet)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(url))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify((int) System.currentTimeMillis(), builder.build());

        } catch (Exception e) {
            Log.e(TAG, "Notification error: " + e.getMessage());
        }
    }
}
