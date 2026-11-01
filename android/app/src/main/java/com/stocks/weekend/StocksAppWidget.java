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
                // 실시간 ON: 즉시 1회 동기화 및 30초 주기 스케줄 시작
                scheduleNextSync(context);
                syncWidgetData(context);
            } else {
                // 실시간 OFF: 스케줄 완전 취소, 네트워크 통신 절대 중지
                cancelPeriodicSync(context);
            }
        } else if (ACTION_PERIODIC_SYNC.equals(action)) {
            boolean isRealtimeOn = prefs.getBoolean(KEY_REALTIME_ON, false);
            if (isRealtimeOn) {
                syncWidgetData(context);
                scheduleNextSync(context); // 다음 30초 후 재등록
            } else {
                // OFF 상태인 경우 네트워크 통신 일절 하지 않음
                cancelPeriodicSync(context);
            }
        } else if (ACTION_SYNC.equals(action)) {
            // 수동 동기화 버튼 클릭 시 1회 갱신
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

        long triggerAt = SystemClock.elapsedRealtime() + 30000; // 30초 후
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

    private void syncWidgetData(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, StocksAppWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);

        if (appWidgetIds == null || appWidgetIds.length == 0) return;

        // "동기화 중..." UI 피드백
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

                // Fetch Snapshot for SKHX, SMSN, MU, PLTR, NVDA, META, TSLA (Exact assets mapping)
                String snapJson = fetchHttp("https://hlkr.co.kr/api/snapshot?_t=" + System.currentTimeMillis());
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

                        // Attach pending intents
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

                        // Realtime Toggle UI
                        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        boolean isRealtime = sp.getBoolean(KEY_REALTIME_ON, false);
                        if (isRealtime) {
                            views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 ON (30s)");
                            views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#10B981"));
                        } else {
                            views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 OFF");
                            views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#94A3B8"));
                        }

                        // Update Sync Time
                        String timeStr = new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date());
                        views.setTextViewText(R.id.widget_sync_btn, "🔄 " + timeStr);

                        // 12 Items
                        applyStock(views, R.id.w_item_1, finalKr200);
                        applyStock(views, R.id.w_item_2, finalNq100);
                        applyStock(views, R.id.w_item_3, 0.45);
                        applyStock(views, R.id.w_item_4, -0.32);
                        applyStock(views, R.id.w_item_5, finalSkhx);
                        applyStock(views, R.id.w_item_6, finalSmsn);
                        applyStock(views, R.id.w_item_7, finalSem);
                        applyStock(views, R.id.w_item_8, finalMu);
                        applyStock(views, R.id.w_item_9, finalPltr);
                        applyStock(views, R.id.w_item_10, finalNvda);
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
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setUseCaches(false);
            conn.setDefaultUseCaches(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            conn.setRequestProperty("Pragma", "no-cache");
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
