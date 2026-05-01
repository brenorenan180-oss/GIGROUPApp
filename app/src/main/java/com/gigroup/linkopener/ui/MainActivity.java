package com.gigroup.linkopener.ui;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.gigroup.linkopener.R;
import com.gigroup.linkopener.service.AutoOpenService;
import com.gigroup.linkopener.util.*;
@SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
public class MainActivity extends AppCompatActivity {
    public static WebView webView;
    private ProgressBar pb;
    private String bridge,content;
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);setContentView(R.layout.activity_main);
        Toolbar tb=findViewById(R.id.toolbar);setSupportActionBar(tb);
        if(getSupportActionBar()!=null)getSupportActionBar().setTitle("GIGROUP Monitor");
        pb=findViewById(R.id.progress_bar);webView=findViewById(R.id.web_view);
        bridge=ScriptLoader.load(this,"scripts/android_bridge.js");
        content=ScriptLoader.load(this,"scripts/content.js");
        setup();
        Intent svc=new Intent(this,AutoOpenService.class);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)startForegroundService(svc);else startService(svc);
        webView.loadUrl("https://web.whatsapp.com/");
    }
    private void setup(){
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);
        s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        webView.addJavascriptInterface(new com.gigroup.linkopener.util.JsBridge(this,new com.gigroup.linkopener.util.StorageManager(this)),"AndroidBridge");
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String u,Bitmap f){pb.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView v,String u){
                pb.setVisibility(View.GONE);
                inject(bridge);
                if(u.contains("web.whatsapp.com"))inject(content);
                if(u.contains("eventuais.gigroup.com.br/oportunidade")){
                    String f=ScriptLoader.load(MainActivity.this,"scripts/content_form.js");inject(f);}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                String u=r.getUrl().toString();
                if(u.contains("eventuais.gigroup.com.br/oportunidade")){
                    Intent i=new Intent(MainActivity.this,FormActivity.class);i.putExtra("url",u);startActivity(i);return true;}
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){pb.setProgress(p);if(p==100)pb.setVisibility(View.GONE);}
        });
    }
    private void inject(String js){if(js==null)return;webView.post(()->webView.evaluateJavascript(js,null));}
    @Override public boolean onCreateOptionsMenu(Menu m){getMenuInflater().inflate(R.menu.main_menu,m);return true;}
    @Override public boolean onOptionsItemSelected(MenuItem i){
        if(i.getItemId()==R.id.action_settings){startActivity(new Intent(this,SettingsActivity.class));return true;}
        if(i.getItemId()==R.id.action_reload){webView.reload();return true;}
        return super.onOptionsItemSelected(i);
    }
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
    @Override protected void onPause(){super.onPause();CookieManager.getInstance().flush();}
    @Override protected void onDestroy(){if(webView!=null){webView.destroy();webView=null;}super.onDestroy();}
}
