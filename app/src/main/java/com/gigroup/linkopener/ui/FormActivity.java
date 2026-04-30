package com.gigroup.linkopener.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.gigroup.linkopener.R;
import com.gigroup.linkopener.util.JsBridge;
import com.gigroup.linkopener.util.ScriptLoader;
import com.gigroup.linkopener.util.StorageManager;

/**
 * FormActivity — abre o link de vaga, injeta bridge + content_form.js
 * e retorna automaticamente ao WhatsApp após FORM_PREENCHIDO.
 */
public class FormActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private String bridgeScript;
    private String formScript;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Vaga GIGROUP");
        }

        progressBar = findViewById(R.id.progress_bar);
        webView     = findViewById(R.id.web_view);

        bridgeScript = ScriptLoader.load(this, "scripts/android_bridge.js");
        formScript   = ScriptLoader.load(this, "scripts/content_form.js");

        configureWebView();

        String url = getIntent().getStringExtra("url");
        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        } else {
            finish();
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        // User-agent mobile para o formulário
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        StorageManager storage = new StorageManager(this);

        // Bridge com callback para fechar esta Activity quando FORM_PREENCHIDO
        JsBridge bridge = new JsBridge(this, storage) {
            @Override
            public void onFormPreenchido() {
                // Retorno ao WhatsApp após 8s (o bridge JS já navega, aqui apenas fecha a Activity)
                webView.postDelayed(() -> finish(), 9000);
            }
        };

        webView.addJavascriptInterface(bridge, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);

                // Injeta bridge em TODAS as páginas
                injectScript(bridgeScript);

                // Injeta form script nas páginas de oportunidade e gigroup
                if (url.contains("gigroup") || url.contains("eventuais")) {
                    // Delay para o SPA renderizar
                    webView.postDelayed(() -> injectScript(formScript), 600);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progressBar.setProgress(p);
                if (p == 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (getSupportActionBar() != null && title != null && !title.isEmpty()) {
                    getSupportActionBar().setSubtitle(title);
                }
            }
        });
    }

    private void injectScript(String script) {
        if (script == null || script.isEmpty()) return;
        final String js = script;
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
