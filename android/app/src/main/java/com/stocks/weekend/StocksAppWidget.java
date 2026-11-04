package com.stocks.weekend;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
    public static final String ACTION_TOGGLE_REALTIME = "com.stocks.weekend.ACTION_TOGGLE_REALTIME";
    public static final String ACTION_PERIODIC_SYNC = "com.stocks.weekend.ACTION_PERIODIC_SYNC";
    private static final String PREF_NAME = "stocks_widget_prefs";
    private static final String KEY_REALTIME_ON = "realtime_on";

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

        String action = intent.getAction();
        if (action == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        if (ACTION_TOGGLE_REALTIME.equals(action)) {
            boolean current = prefs.getBoolean(KEY_REALTIME_ON, false);
            boolean next = !current;
            prefs.edit().putBoolean(KEY_REALTIME_ON, next).apply();

            updateToggleUi(context, next);

            if (next) {
                scheduleNextSync(context);
                syncWidgetData(context);
            } else {
                cancelPeriodicSync(context);
            }
        } else if (ACTION_PERIODIC_SYNC.equals(action)) {
            boolean isRealtimeOn = prefs.getBoolean(KEY_REALTIME_ON, false);
            if (isRealtimeOn) {
                syncWidgetData(context);
                scheduleNextSync(context);
            } else {
                cancelPeriodicSync(context);
            }
        } else if (ACTION_SYNC.equals(action)) {
            syncWidgetData(context);
        }
    }

    private static void scheduleNextSync(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, StocksAppWidget.class);
        intent.setAction(ACTION_PERIODIC_SYNC);
        PendingIntent pi = PendingIntent.getBroadcast(context, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAt = SystemClock.elapsedRealtime() + 30000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
        } else {
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
        }
    }

    private static void cancelPeriodicSync(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, StocksAppWidget.class);
        intent.setAction(ACTION_PERIODIC_SYNC);
        PendingIntent pi = PendingIntent.getBroadcast(context, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    private static void updateToggleUi(Context context, boolean isRealtimeOn) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, StocksAppWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
        if (appWidgetIds == null || appWidgetIds.length == 0) return;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stocks_6x6);
        if (isRealtimeOn) {
            views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 ON (30s)");
            views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#10B981"));
        } else {
            views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 OFF");
            views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#94A3B8"));
        }
        appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, views);
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

            // Click Toggle Realtime -> ACTION_TOGGLE_REALTIME
            Intent toggleIntent = new Intent(context, StocksAppWidget.class);
            toggleIntent.setAction(ACTION_TOGGLE_REALTIME);
            PendingIntent togglePendingIntent = PendingIntent.getBroadcast(context, 101, toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_toggle_realtime, togglePendingIntent);

            // Click Sync button -> ACTION_SYNC
            Intent syncIntent = new Intent(context, StocksAppWidget.class);
            syncIntent.setAction(ACTION_SYNC);
            PendingIntent syncPendingIntent = PendingIntent.getBroadcast(context, 100, syncIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_sync_btn, syncPendingIntent);

            // Reflect current Realtime ON/OFF state
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean isRealtimeOn = prefs.getBoolean(KEY_REALTIME_ON, false);
            if (isRealtimeOn) {
                views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 ON (30s)");
                views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#10B981"));
            } else {
                views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 OFF");
                views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#94A3B8"));
            }

            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isWeekendSession() {
        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        int day = cal.get(java.util.Calendar.DAY_OF_WEEK); // 1: Sunday, 7: Saturday, 2: Monday
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        if (day == java.util.Calendar.SATURDAY && hour >= 6) return true;
        if (day == java.util.Calendar.SUNDAY) return true;
        if (day == java.util.Calendar.MONDAY && hour < 7) return true;
        return false;
    }

    private static double parseMarketChange(String html, String key, double fallback) {
        if (html == null) return fallback;
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"key\":\"" + key + "\",.*?\"changePct\":([+\\-]?[0-9.]+)");
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) {
                return Math.round(Double.parseDouble(m.group(1)) * 100.0) / 100.0;
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private void syncWidgetData(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, StocksAppWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);

        if (appWidgetIds == null || appWidgetIds.length == 0) return;

        RemoteViews loadingViews = new RemoteViews(context.getPackageName(), R.layout.widget_stocks_6x6);
        loadingViews.setTextViewText(R.id.widget_sync_btn, "⏳ 갱신 중...");
        appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, loadingViews);

        executor.execute(() -> {
            try {
                long nowTs = System.currentTimeMillis();
                boolean isWeekend = isWeekendSession();

                String name1, name2, name3, name4;
                double chg1 = 0.0, chg2 = 0.0, chg3 = 0.0, chg4 = 0.0;

                if (isWeekend) {
                    name1 = "W 코스피200";
                    name2 = "W 나스닥100";
                    name3 = "W Tech 100";
                    name4 = "W Oil (Crude)";
                    chg1 = -1.76;
                    chg2 = 0.52;
                    chg3 = 0.45;
                    chg4 = -0.32;

                    String weekendJson = fetchHttp("https://hlkr.co.kr/api/weekend-session?_t=" + nowTs);
                    if (weekendJson != null) {
                        try {
                            JSONObject wObj = new JSONObject(weekendJson);
                            if (wObj.has("kospi")) chg1 = wObj.getJSONObject("kospi").optDouble("changePct", chg1);
                            if (wObj.has("nasdaq")) chg2 = wObj.getJSONObject("nasdaq").optDouble("changePct", chg2);
                        } catch (Exception ignored) {}
                    }
                } else {
                    name1 = "코스피 야간선물";
                    name2 = "나스닥 100";
                    name3 = "S&P 500";
                    name4 = "WTI 원유";
                    chg1 = 1.17;
                    chg2 = -0.32;
                    chg3 = -0.52;
                    chg4 = -0.91;

                    // 1. 코스피 야간선물
                    String kFutJson = fetchHttp("https://hlkr.co.kr/api/kospi-futures?_t=" + nowTs);
                    if (kFutJson != null) {
                        try {
                            JSONObject kObj = new JSONObject(kFutJson);
                            if (kObj.has("kospiNgt")) {
                                chg1 = kObj.getJSONObject("kospiNgt").optDouble("changePct", chg1);
                            }
                        } catch (Exception ignored) {}
                    }

                    // 2 & 3. 나스닥 100 & S&P 500
                    String marketHtml = fetchHttp("https://hlkr.co.kr/market?_t=" + nowTs);
                    if (marketHtml != null) {
                        chg2 = parseMarketChange(marketHtml, "nasdaq", chg2);
                        chg3 = parseMarketChange(marketHtml, "sp500", chg3);
                    }

                    // 4. WTI 원유
                    String macroJson = fetchHttp("https://hlkr.co.kr/api/dash-macro?_t=" + nowTs);
                    if (macroJson != null) {
                        try {
                            JSONObject mObj = new JSONObject(macroJson);
                            JSONArray items = mObj.optJSONArray("items");
                            if (items != null) {
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject it = items.getJSONObject(i);
                                    if ("wti".equals(it.optString("key"))) {
                                        chg4 = Math.round(it.optDouble("changePct", chg4) * 100.0) / 100.0;
                                        break;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }

                // Fetch Snapshot for SKHX, SMSN, MU, PLTR, NVDA, META, TSLA (Exact assets mapping)
                String snapJson = fetchHttp("https://hlkr.co.kr/api/snapshot?_t=" + nowTs);
                double skhxChg = -0.38, smsnChg = -2.68, muChg = 2.35, pltrChg = 0.49, nvdaChg = -0.09, metaChg = -0.09, tslaChg = 0.06;
                double semRefKrw = 1196000;
                if (snapJson != null) {
                    try {
                        JSONObject snapObj = new JSONObject(snapJson);
                        JSONArray assets = snapObj.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject it = assets.getJSONObject(i);
                                JSONObject assetObj = it.optJSONObject("asset");
                                String sym = assetObj != null ? assetObj.optString("symbol") : it.optString("symbol");
                                double prem = it.optDouble("premiumPct", Double.NaN);
                                if (!Double.isNaN(prem)) {
                                    if ("SKHX".equals(sym)) skhxChg = Math.round(prem * 100.0) / 100.0;
                                    else if ("SMSN".equals(sym)) smsnChg = Math.round(prem * 100.0) / 100.0;
                                    else if ("MU".equals(sym)) muChg = Math.round(prem * 100.0) / 100.0;
                                    else if ("PLTR".equals(sym)) pltrChg = Math.round(prem * 100.0) / 100.0;
                                    else if ("NVDA".equals(sym)) nvdaChg = Math.round(prem * 100.0) / 100.0;
                                    else if ("META".equals(sym)) metaChg = Math.round(prem * 100.0) / 100.0;
                                    else if ("TSLA".equals(sym)) tslaChg = Math.round(prem * 100.0) / 100.0;
                                }
                                if ("SAMSUNGEM".equals(sym)) {
                                    semRefKrw = it.optDouble("refPriceKrw", 1196000);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Fetch Samsung EM Binance Futures
                String binanceJson = fetchHttp("https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=SAMSUNGEMUSDT&_t=" + nowTs);
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

                final String finalName1 = name1;
                final String finalName2 = name2;
                final String finalName3 = name3;
                final String finalName4 = name4;
                final double finalChg1 = chg1;
                final double finalChg2 = chg2;
                final double finalChg3 = chg3;
                final double finalChg4 = chg4;
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

                        Intent appIntent = new Intent(context, MainActivity.class);
                        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        PendingIntent appPendingIntent = PendingIntent.getActivity(context, 0, appIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent);

                        Intent toggleIntent = new Intent(context, StocksAppWidget.class);
                        toggleIntent.setAction(ACTION_TOGGLE_REALTIME);
                        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(context, 101, toggleIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        views.setOnClickPendingIntent(R.id.widget_toggle_realtime, togglePendingIntent);

                        Intent syncIntent = new Intent(context, StocksAppWidget.class);
                        syncIntent.setAction(ACTION_SYNC);
                        PendingIntent syncPendingIntent = PendingIntent.getBroadcast(context, 100, syncIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        views.setOnClickPendingIntent(R.id.widget_sync_btn, syncPendingIntent);

                        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        boolean isRealtime = sp.getBoolean(KEY_REALTIME_ON, false);
                        if (isRealtime) {
                            views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 ON (30s)");
                            views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#10B981"));
                        } else {
                            views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 OFF");
                            views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#94A3B8"));
                        }

                        String timeStr = new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date());
                        views.setTextViewText(R.id.widget_sync_btn, "🔄 " + timeStr);

                        // Dynamic names for items 1-4 based on Weekend vs Weekday
                        views.setTextViewText(R.id.w_sub_1, finalName1);
                        views.setTextViewText(R.id.w_sub_2, finalName2);
                        views.setTextViewText(R.id.w_sub_3, finalName3);
                        views.setTextViewText(R.id.w_sub_4, finalName4);

                        applyStock(views, R.id.w_item_1, finalChg1);
                        applyStock(views, R.id.w_item_2, finalChg2);
                        applyStock(views, R.id.w_item_3, finalChg3);
                        applyStock(views, R.id.w_item_4, finalChg4);
                        applyStock(views, R.id.w_item_5, finalSkhx);
                        applyStock(views, R.id.w_item_6, finalSmsn);
                        applyStock(views, R.id.w_item_7, finalSem);
                        applyStock(views, R.id.w_item_8, finalMu);
                        applyStock(views, R.id.w_item_9, finalPltr);
                        applyStock(views, R.id.w_item_10, finalNvda);
                        applyStock(views, R.id.w_item_11, finalMeta);
                        applyStock(views, R.id.w_item_12, finalTsla);
                        applyStock(views, R.id.w_item_11, finalMeta);
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
            conn.setConnectTimeout(1500); // Fast 1.5s timeout
            conn.setReadTimeout(1500);
            conn.setUseCaches(false);
            conn.setDefaultUseCaches(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            conn.setRequestProperty("Pragma", "no-cache");
            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 8192);
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
