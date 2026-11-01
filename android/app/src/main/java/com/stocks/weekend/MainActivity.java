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
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static volatile boolean isAppInForeground = false;
    private static final ExecutorService parallelPool = Executors.newFixedThreadPool(6);

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
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Native HTTP Bridge with High-Speed Parallel Batch Support
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
            webView.evaluateJavascript("if (typeof onAppForegroundStateChange === 'function') onAppForegroundStateChange(true);", null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        if (webView != null) {
            webView.evaluateJavascript("if (typeof onAppForegroundStateChange === 'function') onAppForegroundStateChange(false);", null);
            webView.onPause();
            webView.pauseTimers(); // Complete halt
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

        // Fast Parallel Batch Fetch: Executes all 5 queries simultaneously across threads (0.2~0.4s total)
        @JavascriptInterface
        public String fetchBatchAll(String weekendUrl, String snapshotUrl, String binanceUrl, String igTechUrl, String igOilUrl) {
            if (!isAppInForeground) {
                return "ERROR:BACKGROUND_BLOCKED";
            }

            try {
                Future<String> fWeekend = parallelPool.submit(() -> fastHttpGet(weekendUrl, 2500));
                Future<String> fSnapshot = parallelPool.submit(() -> fastHttpGet(snapshotUrl, 2500));
                Future<String> fBinance = parallelPool.submit(() -> fastHttpGet(binanceUrl, 2500));
                Future<String> fIgTech = parallelPool.submit(() -> fastHttpGet(igTechUrl, 2500));
                Future<String> fIgOil = parallelPool.submit(() -> fastHttpGet(igOilUrl, 2500));

                JSONObject res = new JSONObject();
                res.put("weekend", safeGet(fWeekend, 2600));
                res.put("snapshot", safeGet(fSnapshot, 2600));
                res.put("binance", safeGet(fBinance, 2600));
                res.put("igTech", safeGet(fIgTech, 2600));
                res.put("igOil", safeGet(fIgOil, 2600));

                return res.toString();
            } catch (Exception e) {
                return "ERROR:" + e.getMessage();
            }
        }

        @JavascriptInterface
        public String fetchUrl(String urlString) {
            if (!isAppInForeground) {
                return "ERROR:BACKGROUND_BLOCKED";
            }
            return fastHttpGet(urlString, 3500);
        }

        private static String safeGet(Future<String> future, long timeoutMs) {
            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return null;
            }
        }

        private static String fastHttpGet(String urlString, int timeoutMs) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setUseCaches(false);
                conn.setDefaultUseCaches(false);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                conn.setRequestProperty("Pragma", "no-cache");

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 8192);
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
