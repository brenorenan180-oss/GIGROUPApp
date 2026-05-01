package com.gigroup.linkopener.ui;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.gigroup.linkopener.R;
import com.gigroup.linkopener.util.*;
@SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
public class FormActivity extends AppCompatActivity {
    private WebView wv;private ProgressBar pb;
    private String bridge,form;
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);setContentView(R.layout.activity_form);
        Toolbar tb=findViewById(R.id.toolbar);setSupportActionBar(tb);
        if(getSupportActionBar()!=null){getSupportActionBar().setDisplayHomeAsUpEnabled(true);getSupportActionBar().setTitle("Vaga GIGROUP");}
        pb=findViewById(R.id.progress_bar);wv=findViewById(R.id.web_view);
        bridge=ScriptLoader.load(this,"scripts/android_bridge.js");
        form=ScriptLoader.load(this,"scripts/content_form.js");
        setup();
        String url=getIntent().getStringExtra("url");
        if(url!=null&&!url.isEmpty())wv.loadUrl(url);else finish();
    }
    private void setup(){
        WebSettings s=wv.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(wv,true);
        StorageManager st=new StorageManager(this);
        JsBridge br=new JsBridge(this,st){@Override public void onFormPreenchido(){wv.postDelayed(()->finish(),9000);}};
        wv.addJavascriptInterface(br,"AndroidBridge");
        wv.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String u,Bitmap f){pb.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView v,String u){
                pb.setVisibility(View.GONE);inject(bridge);
                if(u.contains("gigroup")||u.contains("eventuais"))wv.postDelayed(()->inject(form),600);
            }
        });
        wv.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){pb.setProgress(p);if(p==100)pb.setVisibility(View.GONE);}
            @Override public void onReceivedTitle(WebView v,String t){if(getSupportActionBar()!=null&&t!=null)getSupportActionBar().setSubtitle(t);}
        });
    }
    private void inject(String js){if(js==null)return;wv.post(()->wv.evaluateJavascript(js,null));}
    @Override public boolean onOptionsItemSelected(android.view.MenuItem i){if(i.getItemId()==android.R.id.home){onBackPressed();return true;}return super.onOptionsItemSelected(i);}
    @Override public void onBackPressed(){if(wv!=null&&wv.canGoBack())wv.goBack();else finish();}
    @Override protected void onDestroy(){if(wv!=null){wv.destroy();wv=null;}super.onDestroy();}
}
