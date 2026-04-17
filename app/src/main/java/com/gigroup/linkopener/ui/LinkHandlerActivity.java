package com.gigroup.linkopener.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
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
 * LinkHandlerActivity — abre o link de vaga numa WebView dedicada
 * e injeta android_bridge.js + content_form.js para preencher automaticamente.
 */
public class LinkHandlerActivity extends AppCompatActivity {

    private WebView     webView;
    private ProgressBar progressBar;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_handler);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Vaga GIGROUP");
        }

        progressBar = findViewById(R.id.progress_bar);
        webView     = findViewById(R.id.web_view);

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
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"
        );
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        StorageManager storage = new StorageManager(this);
        webView.addJavascriptInterface(new JsBridge(this, storage), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);

                // Injeta bridge + form script em qualquer página de oportunidade
                if (url.contains("gigroup") || url.contains("eventuais")) {
                    injectScripts();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (getSupportActionBar() != null && title != null) {
                    getSupportActionBar().setSubtitle(title);
                }
            }
        });
    }

    private void injectScripts() {
        String bridge = ScriptLoader.loadAsset(this, "scripts/android_bridge.js");
        String form   = ScriptLoader.loadAsset(this, "scripts/content_form.js");

        if (bridge != null) {
            final String b = bridge;
            webView.post(() -> webView.evaluateJavascript(b, null));
        }
        if (form != null) {
            final String f = form;
            // Pequeno delay para o DOM estabilizar
            webView.postDelayed(() -> webView.evaluateJavascript(f, null), 500);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
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
