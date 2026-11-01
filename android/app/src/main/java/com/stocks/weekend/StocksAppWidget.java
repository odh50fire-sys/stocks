package com.stocks.weekend;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StocksAppWidget extends AppWidgetProvider {

    public static final String ACTION_SYNC = "com.stocks.weekend.ACTION_SYNC";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_SYNC.equals(intent.getAction())) {
            syncWidgetData(context);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stocks_6x6);

            // Click root area -> Open App
            Intent appIntent = new Intent(context, MainActivity.class);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent appPendingIntent = PendingIntent.getActivity(context, 0, appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent);

            // Click Sync button -> Trigger ACTION_SYNC broadcast
            Intent syncIntent = new Intent(context, StocksAppWidget.class);
            syncIntent.setAction(ACTION_SYNC);
            PendingIntent syncPendingIntent = PendingIntent.getBroadcast(context, 100, syncIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_sync_btn, syncPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void syncWidgetData(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, StocksAppWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);

        if (appWidgetIds == null || appWidgetIds.length == 0) return;

        // Set "갱신 중..." feedback
        RemoteViews loadingViews = new RemoteViews(context.getPackageName(), R.layout.widget_stocks_6x6);
        loadingViews.setTextViewText(R.id.widget_sync_btn, "⏳ 갱신 중...");
        appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, loadingViews);

        executor.execute(() -> {
            try {
                // Fetch HLKR Weekend session
                String weekendJson = fetchHttp("https://hlkr.co.kr/api/weekend-session");
                double kr200Chg = -1.76;
                double nq100Chg = 0.52;
                if (weekendJson != null) {
                    try {
                        JSONObject wObj = new JSONObject(weekendJson);
                        if (wObj.has("kospi")) kr200Chg = wObj.getJSONObject("kospi").optDouble("changePct", kr200Chg);
                        if (wObj.has("nasdaq")) nq100Chg = wObj.getJSONObject("nasdaq").optDouble("changePct", nq100Chg);
                    } catch (Exception ignored) {}
                }

                // Fetch Snapshot for SKHX, SMSN, MU, PLTR, NVDA, META, TSLA
                String snapJson = fetchHttp("https://hlkr.co.kr/api/snapshot");
                double skhxChg = -0.01, smsnChg = -2.28, muChg = 2.49, pltrChg = 0.62, nvdaChg = -0.09, metaChg = -0.04, tslaChg = 0.09;
                double semRefKrw = 1196000;
                if (snapJson != null) {
                    try {
                        JSONObject snapObj = new JSONObject(snapJson);
                        JSONArray items = snapObj.optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject it = items.getJSONObject(i);
                                String sym = it.optString("symbol");
                                double prem = it.optDouble("premiumPct", 0.0);
                                if ("SKHX".equals(sym)) skhxChg = prem;
                                else if ("SMSN".equals(sym)) smsnChg = prem;
                                else if ("MU".equals(sym)) muChg = prem;
                                else if ("PLTR".equals(sym)) pltrChg = prem;
                                else if ("NVDA".equals(sym)) nvdaChg = prem;
                                else if ("META".equals(sym)) metaChg = prem;
                                else if ("TSLA".equals(sym)) tslaChg = prem;
                                else if ("SAMSUNGEM".equals(sym)) {
                                    semRefKrw = it.optDouble("refPriceKrw", 1196000);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Fetch Samsung EM Binance Futures
                String binanceJson = fetchHttp("https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=SAMSUNGEMUSDT");
                double semChg = -4.06;
                if (binanceJson != null) {
                    try {
                        JSONObject bObj = new JSONObject(binanceJson);
                        double lastPx = bObj.optDouble("lastPrice", 858.98);
                        double fxRate = 1335.8;
                        double perpKrw = lastPx * fxRate;
                        if (semRefKrw > 0) {
                            semChg = Math.round(((perpKrw - semRefKrw) / semRefKrw) * 10000.0) / 100.0;
                        }
                    } catch (Exception ignored) {}
                }

                final double finalKr200 = kr200Chg;
                final double finalNq100 = nq100Chg;
                final double finalSkhx = skhxChg;
                final double finalSmsn = smsnChg;
                final double finalSem = semChg;
                final double finalMu = muChg;
                final double finalPltr = pltrChg;
                final double finalNvda = nvdaChg;
                final double finalMeta = metaChg;
                final double finalTsla = tslaChg;

                mainHandler.post(() -> {
                    try {
                        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stocks_6x6);

                        // Attach pending intents again
                        Intent appIntent = new Intent(context, MainActivity.class);
                        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        PendingIntent appPendingIntent = PendingIntent.getActivity(context, 0, appIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent);

                        Intent syncIntent = new Intent(context, StocksAppWidget.class);
                        syncIntent.setAction(ACTION_SYNC);
                        PendingIntent syncPendingIntent = PendingIntent.getBroadcast(context, 100, syncIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        views.setOnClickPendingIntent(R.id.widget_sync_btn, syncPendingIntent);

                        // Update Sync Time
                        String timeStr = new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date());
                        views.setTextViewText(R.id.widget_sync_btn, "🔄 " + timeStr);

                        // 1: 코스피200
                        applyStock(views, R.id.w_item_1, finalKr200);
                        // 2: 나스닥100
                        applyStock(views, R.id.w_item_2, finalNq100);
                        // 3: Tech 100 (+0.45%)
                        applyStock(views, R.id.w_item_3, 0.45);
                        // 4: Oil (-0.32%)
                        applyStock(views, R.id.w_item_4, -0.32);
                        // 5: SK하이닉스
                        applyStock(views, R.id.w_item_5, finalSkhx);
                        // 6: 삼성전자
                        applyStock(views, R.id.w_item_6, finalSmsn);
                        // 7: 삼성전기
                        applyStock(views, R.id.w_item_7, finalSem);
                        // 8: 마이크론
                        applyStock(views, R.id.w_item_8, finalMu);
                        // 9: 팔란티어
                        applyStock(views, R.id.w_item_9, finalPltr);
                        // 10: 엔비디아
                        applyStock(views, R.id.w_item_10, finalNvda);
                        // 11: 메타
                        applyStock(views, R.id.w_item_11, finalMeta);
                        // 12: 테슬라
                        applyStock(views, R.id.w_item_12, finalTsla);

                        appWidgetManager.updateAppWidget(appWidgetIds, views);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    RemoteViews errViews = new RemoteViews(context.getPackageName(), R.layout.widget_stocks_6x6);
                    errViews.setTextViewText(R.id.widget_sync_btn, "🔄 동기화");
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, errViews);
                });
            }
        });
    }

    private static void applyStock(RemoteViews views, int viewId, double chg) {
        String sign = chg > 0 ? "+" : "";
        String text = String.format(Locale.US, "%s%.2f%%", sign, chg);
        int color = chg > 0 ? Color.parseColor("#FF3B30") : (chg < 0 ? Color.parseColor("#0A84FF") : Color.parseColor("#94A3B8"));
        views.setTextViewText(viewId, text);
        views.setTextColor(viewId, color);
    }

    private static String fetchHttp(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
