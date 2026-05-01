package com.gigroup.linkopener.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.gigroup.linkopener.ui.MainActivity;

/**
 * NOVO ARQUIVO — WakefulAutoChecker
 * ────────────────────────────────────────────────────────────
 * Usa AlarmManager para acordar o dispositivo periodicamente
 * e verificar se o WhatsApp Web precisa ser recarregado.
 *
 * Funciona mesmo com tela bloqueada via WAKE_LOCK.
 *
 * Como registrar no AndroidManifest (ADICIONAR ao existente):
 * <receiver android:name=".service.WakefulAutoChecker" android:exported="true"/>
 *
 * Como iniciar (chamar em MainActivity.onCreate):
 * WakefulAutoChecker.schedule(this);
 */
public class WakefulAutoChecker extends BroadcastReceiver {

    private static final String TAG          = "GIGROUP-WAC";
    private static final String ACTION_CHECK = "com.gigroup.linkopener.CHECK";
    private static final long   INTERVAL_MS  = 60_000L; // 1 minuto

    @Override
    public void onReceive(Context ctx, Intent intent) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "GIGROUP:WakefulCheck");
        wl.acquire(10_000L); // máximo 10s

        try {
            Log.i(TAG, "WakefulAutoChecker disparado");
            checkAndReload(ctx);
        } finally {
            if (wl.isHeld()) wl.release();
        }

        // Reagenda para o próximo ciclo
        schedule(ctx);
    }

    private void checkAndReload(Context ctx) {
        if (MainActivity.webView != null) {
            MainActivity.webView.post(() -> {
                String url = MainActivity.webView.getUrl();
                if (url == null || url.isEmpty() || url.equals("about:blank")) {
                    Log.w(TAG, "WebView vazio — recarregando WhatsApp Web");
                    MainActivity.webView.loadUrl("https://web.whatsapp.com/");
                } else {
                    // Re-injeta bridge para garantir que detectores estão ativos
                    String js = "if(!window.__GIGROUP_BRIDGE_V3__){ location.reload(); }";
                    MainActivity.webView.evaluateJavascript(js, null);
                    Log.d(TAG, "WebView OK: " + url);
                }
            });
        } else {
            // WebView não existe — abre MainActivity
            Log.w(TAG, "WebView nulo — tentando abrir MainActivity");
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(i);
        }
    }

    // ── Agenda o próximo disparo ──────────────────────────────────────────
    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, WakefulAutoChecker.class);
        i.setAction(ACTION_CHECK);

        PendingIntent pi = PendingIntent.getBroadcast(
            ctx, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long trigger = System.currentTimeMillis() + INTERVAL_MS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, trigger, pi);
        }

        Log.d(TAG, "Próxima verificação agendada em " + INTERVAL_MS / 1000 + "s");
    }

    // ── Cancela agendamento ───────────────────────────────────────────────
    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, WakefulAutoChecker.class);
        i.setAction(ACTION_CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(
            ctx, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        Log.d(TAG, "Agendamento cancelado");
    }
}
