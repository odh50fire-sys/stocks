package com.stocks.weekend;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static volatile boolean isAppInForeground = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Native HTTP Bridge with Foreground Check
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // Load local standalone SPA assets
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
            // Inform JavaScript that the app is now in foreground
            webView.evaluateJavascript("if (typeof onAppForegroundStateChange === 'function') onAppForegroundStateChange(true);", null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        if (webView != null) {
            // Inform JavaScript before pause to cancel all intervals
            webView.evaluateJavascript("if (typeof onAppForegroundStateChange === 'function') onAppForegroundStateChange(false);", null);
            webView.onPause();
            webView.pauseTimers(); // Completely halt all JavaScript timers at the OS level
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        isAppInForeground = false;
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    protected void onDestroy() {
        isAppInForeground = false;
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    public static class WebAppInterface {
        Context context;

        WebAppInterface(Context c) {
            context = c;
        }

        @JavascriptInterface
        public boolean isForeground() {
            return isAppInForeground;
        }

        @JavascriptInterface
        public String fetchUrl(String urlString) {
            // Strict Battery & Network Protection: If app is not in foreground, reject network call immediately!
            if (!isAppInForeground) {
                return "ERROR:BACKGROUND_BLOCKED";
            }

            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8");
                conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    return sb.toString();
                } else {
                    return "ERROR:" + code;
                }
            } catch (Exception e) {
                return "ERROR:" + e.getMessage();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
