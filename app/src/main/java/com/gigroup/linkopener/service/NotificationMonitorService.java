package com.gigroup.linkopener.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.gigroup.linkopener.ui.FormActivity;
import com.gigroup.linkopener.util.StorageManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NotificationMonitorService — API oficial do Android para leitura de notificações.
 *
 * Funciona com tela BLOQUEADA, app FECHADO, em segundo plano real.
 * O usuário precisa ativar em: Configurações → Notificações → Acesso a notificações → GIGROUP Monitor
 *
 * Detecta notificações do WhatsApp que contenham:
 * - Nome do grupo alvo ("gigroup", "shopee", "juazeiro")
 * - URL de vaga (eventuais.gigroup.com.br)
 */
public class NotificationMonitorService extends NotificationListenerService {

    private static final String TAG = "GIGROUP-NLS";

    // Pacotes do WhatsApp (oficial e business)
    private static final String[] WA_PACKAGES = {
        "com.whatsapp",
        "com.whatsapp.w4b"
    };

    // Palavras-chave do grupo alvo
    private static final String[] GROUP_KEYWORDS = {
        "gigroup", "shopee", "juazeiro", "simulação", "simulacao"
    };

    // Regex para extrair URL de vaga
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[^\\s\"'<>]+"
    );

    private StorageManager storage;

    @Override
    public void onCreate() {
        super.onCreate();
        storage = new StorageManager(this);
        Log.i(TAG, "✅ NotificationListenerService iniciado");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        // Verifica se é do WhatsApp
        String pkg = sbn.getPackageName();
        boolean isWhatsApp = false;
        for (String wa : WA_PACKAGES) {
            if (wa.equals(pkg)) { isWhatsApp = true; break; }
        }
        if (!isWhatsApp) return;

        // Extrai texto da notificação
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        String title   = extras.getString("android.title",   "");
        String text    = extras.getString("android.text",    "");
        String bigText = extras.getString("android.bigText", "");

        // Texto completo concatenado
        String full = (title + " " + text + " " + bigText).toLowerCase();

        Log.d(TAG, "WA notif — title: " + title + " | text: " + text);

        // Verifica se é do grupo alvo
        boolean isTarget = false;
        for (String kw : GROUP_KEYWORDS) {
            if (full.contains(kw)) { isTarget = true; break; }
        }
        if (!isTarget) return;

        Log.i(TAG, "🎯 Grupo alvo detectado! title=" + title);

        // Procura URL na notificação
        String urlFound = extractUrl(text + " " + bigText);

        if (urlFound != null && isGigroupUrl(urlFound)) {
            // Abre o formulário diretamente
            Log.i(TAG, "🔗 URL encontrada na notificação: " + urlFound);
            openFormUrl(urlFound, text);
        } else {
            // URL não está na notificação — clica na notificação para abrir o WhatsApp
            // O content.js vai detectar o link quando o chat abrir
            Log.i(TAG, "📩 Sem URL na notificação — abrindo WhatsApp para extrair link");
            clickNotification(sbn);
        }
    }

    private String extractUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            String url = m.group();
            if (isGigroupUrl(url)) return url;
        }
        return null;
    }

    private boolean isGigroupUrl(String url) {
        return url != null &&
            (url.contains("gigroup") || url.contains("eventuais"));
    }

    private void openFormUrl(String url, String snippet) {
        if (!storage.isEnabled()) return;
        if (!storage.isTodayAllowed()) return;
        if (storage.isDailyLimitReached()) return;
        if (storage.isLinkOpened(url)) return;

        storage.recordLinkOpened(url, snippet);

        Intent i = new Intent(this, FormActivity.class);
        i.putExtra("url", url);
        i.putExtra("snippet", snippet);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);

        Log.i(TAG, "✅ FormActivity iniciada via NLS: " + url);
    }

    private void clickNotification(StatusBarNotification sbn) {
        try {
            if (sbn.getNotification().contentIntent != null) {
                sbn.getNotification().contentIntent.send();
                Log.i(TAG, "Notificação clicada para abrir WhatsApp");
            }
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível clicar na notificação: " + e.getMessage());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Não precisa fazer nada ao remover
    }
}
