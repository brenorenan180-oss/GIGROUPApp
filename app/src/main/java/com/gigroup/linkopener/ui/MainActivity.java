package com.gigroup.linkopener.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.gigroup.linkopener.R;
import com.gigroup.linkopener.service.MonitorService;
import com.gigroup.linkopener.util.JsBridge;
import com.gigroup.linkopener.util.ScriptLoader;
import com.gigroup.linkopener.util.StorageManager;

public class MainActivity extends AppCompatActivity {

    public static WebView webView;          // acesso pelo JsBridge
    private ProgressBar progressBar;
    private StorageManager storage;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = new StorageManager(this);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("GIGROUP Monitor");
        }

        progressBar = findViewById(R.id.progress_bar);
        webView     = findViewById(R.id.web_view);

        configureWebView();

        // Inicia serviço em foreground
        Intent svc = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        // Carrega WhatsApp Web
        webView.loadUrl("https://web.whatsapp.com/");
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureWebView() {
        WebSettings s = webView.getSettings();

        // JavaScript obrigatório
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        // Desktop user-agent (WhatsApp Web exige)
        s.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"
        );

        // Recursos adicionais
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);

        // Cookies persistentes (mantém sessão do WhatsApp)
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Bridge Java ↔ JavaScript
        webView.addJavascriptInterface(new JsBridge(this, storage), "AndroidBridge");

        // WebViewClient: injeta scripts após página carregar
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);

                if (url.contains("web.whatsapp.com")) {
                    injectGIGROUPScripts(url);
                } else if (url.contains("eventuais.gigroup.com.br/oportunidade")) {
                    injectFormScript();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Links externos abrem no navegador do sistema
                if (!url.contains("web.whatsapp.com") &&
                    !url.contains("eventuais.gigroup.com.br") &&
                    !url.contains("gigroup.com.br")) {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                    return true;
                }

                // Links de vagas: abre noutra aba (LinkHandlerActivity)
                if (url.contains("eventuais.gigroup.com.br/oportunidade")) {
                    Intent i = new Intent(MainActivity.this, LinkHandlerActivity.class);
                    i.putExtra("url", url);
                    startActivity(i);
                    return true;
                }

                return false;
            }
        });

        // WebChromeClient: título e progresso
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (getSupportActionBar() != null && title != null && !title.isEmpty()) {
                    getSupportActionBar().setSubtitle(title);
                }
            }
        });
    }

    // ── Injeta content.js e bridge helper ────────────────────────────────────
    private void injectGIGROUPScripts(String url) {
        // Helper que substitui chrome.runtime.sendMessage → AndroidBridge
        String bridge = ScriptLoader.loadAsset(this, "scripts/android_bridge.js");
        // Script principal de detecção
        String content = ScriptLoader.loadAsset(this, "scripts/content.js");

        if (bridge != null) {
            final String b = bridge;
            webView.post(() -> webView.evaluateJavascript(b, null));
        }
        if (content != null) {
            final String c = content;
            webView.post(() -> webView.evaluateJavascript(c, null));
        }
    }

    // ── Injeta content-form.js na página de vagas ─────────────────────────────
    private void injectFormScript() {
        String form = ScriptLoader.loadAsset(this, "scripts/content_form.js");
        if (form != null) {
            final String f = form;
            webView.post(() -> webView.evaluateJavascript(f, null));
        }
    }

    // ── Menu ──────────────────────────────────────────────────────────────────
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_reload) {
            webView.reload();
            return true;
        }
        if (id == R.id.action_clear_cache) {
            webView.clearCache(true);
            webView.reload();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Back: navegar no histórico do WebView ─────────────────────────────────
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Salva cookies ao pausar
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
