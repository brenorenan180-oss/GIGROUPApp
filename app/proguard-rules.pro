# Mantém o JsBridge — métodos @JavascriptInterface NÃO podem ser ofuscados
-keepclassmembers class com.gigroup.linkopener.util.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Mantém todas as classes do pacote principal
-keep class com.gigroup.linkopener.** { *; }

# WebView com JS
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

# JSON (usado no JsBridge)
-keep class org.json.** { *; }
