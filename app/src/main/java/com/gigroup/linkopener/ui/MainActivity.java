package com.gigroup.linkopener.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
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
import com.gigroup.linkopener.service.AutoOpenService;
import com.gigroup.linkopener.util.JsBridge;
import com.gigroup.linkopener.util.ScriptLoader;
import com.gigroup.linkopener.util.StorageManager;

public class MainActivity extends AppCompatActivity {

    // Referência estática para SettingsActivity comunicar com o WebView
    public static WebView webView;

    private ProgressBar progressBar;
    private StorageManager storage;
    private String bridgeScript;
    private String contentScript;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = new StorageManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("GIGROUP Monitor");

        progressBar = findViewById(R.id.progress_bar);
        webView = findViewById(R.id.web_view);

        // Pré-carrega scripts dos assets (evita I/O no onPageFinished)
        bridgeScript  = ScriptLoader.load(this, "scripts/android_bridge.js");
        contentScript = ScriptLoader.load(this, "scripts/content.js");

        configureWebView();
        startForegroundService();

        webView.loadUrl("https://web.whatsapp.com/");
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(true);

        // User-agent desktop — WhatsApp Web exige
        s.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"
        );

        // Cookies persistentes (mantém sessão)
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Bridge Java ↔ JS
        webView.addJavascriptInterface(new JsBridge(this, storage), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);

                // INJETAR BRIDGE EM TODAS AS PÁGINAS
                injectScript(bridgeScript);

                // content.js apenas no WhatsApp Web
                if (url.contains("web.whatsapp.com")) {
                    injectScript(contentScript);
                }

                // content_form.js na página de vagas
                if (url.contains("eventuais.gigroup.com.br/oportunidade")) {
                    String formScript = ScriptLoader.load(MainActivity.this, "scripts/content_form.js");
                    injectScript(formScript);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                // Links de vaga: abre na FormActivity (preserva sessão do WhatsApp)
                if (url.contains("eventuais.gigroup.com.br/oportunidade")) {
                    Intent i = new Intent(MainActivity.this, FormActivity.class);
                    i.putExtra("url", url);
                    startActivity(i);
                    return true;
                }
                // Links externos: abre no browser do sistema
                if (!url.contains("web.whatsapp.com") && !url.contains("gigroup.com.br")) {
                    Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    startActivity(i);
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progressBar.setProgress(p);
                if (p == 100) progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void injectScript(String script) {
        if (script == null || script.isEmpty()) return;
        final String js = script;
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void startForegroundService() {
        Intent svc = new Intent(this, AutoOpenService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    // Chamado pelo JsBridge quando detecta um link (thread principal)
    public void openFormUrl(String url, String snippet) {
        Intent i = new Intent(this, FormActivity.class);
        i.putExtra("url", url);
        i.putExtra("snippet", snippet);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

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
        if (id == R.id.action_reload) { webView.reload(); return true; }
        if (id == R.id.action_clear_cache) { webView.clearCache(true); webView.reload(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
