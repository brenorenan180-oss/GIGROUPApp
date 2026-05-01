package com.gigroup.linkopener.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.gigroup.linkopener.ui.FormActivity;
import com.gigroup.linkopener.util.StorageManager;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NOVO ARQUIVO — AutoClickNotificationService (AccessibilityService)
 * ──────────────────────────────────────────────────────────────────
 * Lê o conteúdo da tela do WhatsApp (mesmo com app em background)
 * e extrai links de vagas automaticamente — fallbacks A1-A8.
 *
 * ATIVAR EM: Configurações → Acessibilidade → GIGROUP Monitor → Ativar
 *
 * Declarar no AndroidManifest (ADICIONAR ao existente):
 * <service android:name=".service.AutoClickNotificationService"
 *     android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.accessibilityservice.AccessibilityService"/>
 *     </intent-filter>
 *     <meta-data android:name="android.accessibilityservice"
 *         android:resource="@xml/accessibility_service_config"/>
 * </service>
 */
public class AutoClickNotificationService extends AccessibilityService {

    private static final String TAG = "GIGROUP-A11Y";

    private static final String[] WA_PKGS = {"com.whatsapp", "com.whatsapp.w4b"};
    private static final String[] TARGETS  = {"gigroup", "shopee", "juazeiro", "simulação"};
    private static final Pattern  URL_PAT  = Pattern.compile("https?://[^\\s\"'<>]+");

    private StorageManager storage;
    private String lastUrl = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        storage = new StorageManager(this);

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                   | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        Log.i(TAG, "✅ AutoClickNotificationService conectado");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        boolean isWA = false;
        for (String p : WA_PKGS) { if (p.equals(pkg)) { isWA = true; break; } }
        if (!isWA) return;

        // A1: Texto do evento
        String text = event.getText() != null ? event.getText().toString() : "";
        if (!text.isEmpty()) processText(text, "A1-event-text");

        // A2: Descrição de conteúdo
        if (event.getContentDescription() != null) {
            processText(event.getContentDescription().toString(), "A2-content-desc");
        }

        // A3-A8: Varredura da árvore de acessibilidade
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            scanNodeTree(root, 0);
            root.recycle();
        }
    }

    // ── A3-A8: Varredura recursiva da árvore de nós ──────────────────────
    private void scanNodeTree(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 8) return;

        // A3: Texto do nó
        if (node.getText() != null) {
            processText(node.getText().toString(), "A3-node-text");
        }

        // A4: ContentDescription do nó
        if (node.getContentDescription() != null) {
            processText(node.getContentDescription().toString(), "A4-node-desc");
        }

        // A5: ViewIdResourceName (pode conter dados)
        if (node.getViewIdResourceName() != null) {
            String vid = node.getViewIdResourceName();
            if (vid.contains("message") || vid.contains("text")) {
                // Força leitura de texto em campos de mensagem
                AccessibilityNodeInfo.RangeInfo range = node.getRangeInfo();
                if (range != null) {
                    Log.d(TAG, "A5: range info em: " + vid);
                }
            }
        }

        // A6: Busca por URLs em nós filhos
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                scanNodeTree(child, depth + 1);
                child.recycle();
            }
        }
    }

    // ── A7: Verifica se texto é do grupo alvo e extrai URL ───────────────
    private void processText(String text, String source) {
        if (text == null || text.isEmpty()) return;

        String lower = text.toLowerCase();
        boolean isTarget = false;
        for (String t : TARGETS) { if (lower.contains(t)) { isTarget = true; break; } }

        // A8: Extrai URL mesmo sem confirmar grupo alvo (por segurança procura URL diretamente)
        Matcher m = URL_PAT.matcher(text);
        while (m.find()) {
            String url = m.group();
            if (isGigroupUrl(url) && !url.equals(lastUrl)) {
                lastUrl = url;
                Log.i(TAG, "🔗 [" + source + "] URL encontrada: " + url);
                openForm(url, text);
                return;
            }
        }
    }

    private boolean isGigroupUrl(String u) {
        return u != null && (u.contains("gigroup") || u.contains("eventuais"));
    }

    private void openForm(String url, String snippet) {
        if (!storage.isEnabled()) return;
        if (!storage.isTodayAllowed()) return;
        if (storage.isDailyLimitReached()) return;
        if (storage.isLinkOpened(url)) return;

        storage.recordLinkOpened(url, snippet);

        Intent i = new Intent(this, FormActivity.class);
        i.putExtra("url", url);
        i.putExtra("snippet", snippet != null ? snippet.substring(0, Math.min(snippet.length(), 80)) : "");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);

        Log.i(TAG, "✅ FormActivity aberta via AccessibilityService: " + url);
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "AccessibilityService interrompido");
    }
}
