package com.stocks.weekend;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class StocksAppWidget extends AppWidgetProvider {

    public static final String ACTION_SYNC = "com.stocks.weekend.ACTION_SYNC";
    public static final String ACTION_TOGGLE_REALTIME = "com.stocks.weekend.ACTION_TOGGLE_REALTIME";
    public static final String ACTION_PERIODIC_SYNC = "com.stocks.weekend.ACTION_PERIODIC_SYNC";

    private static final String PREF_NAME = "stocks_widget_prefs";
    private static final String KEY_REALTIME_ON = "realtime_on";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String PREF_CACHE_PREFIX = "cache_stock_";
    private static final String PREF_HISTORY_PREFIX = "history_stock_";
    private static final long MAX_HISTORY_MS = 24 * 60 * 60 * 1000L; // 24 Hours
    private static final String BACKUP_FILE_NAME = "stocks_24h_history_backup.json";

    // 6-thread pool for ultra-fast parallel network fetching (completes in ~0.5s)
    private static final ExecutorService networkPool = Executors.newFixedThreadPool(6);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean isSyncInProgress = new AtomicBoolean(false);

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

        RemoteViews views = buildWidgetViews(context, false, null);
        appWidgetManager.updateAppWidget(appWidgetIds, views);
    }

    /**
     * Called by Android OS when the widget is first created or refreshed by launcher/system.
     * Guarantees that the widget ALWAYS displays the last known good cached data and charts,
     * preventing any blank screens or dummy layout resets!
     */
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews views = buildWidgetViews(context, false, null);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isWeekendSession() {
        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        int day = cal.get(java.util.Calendar.DAY_OF_WEEK);
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        if (day == java.util.Calendar.SATURDAY && hour >= 6) return true;
        if (day == java.util.Calendar.SUNDAY) return true;
        if (day == java.util.Calendar.MONDAY && hour < 7) return true;
        return false;
    }

    /**
     * Single Source of Truth for constructing RemoteViews.
     * Binds current pending intents, realtime toggle state, sync status, stock names,
     * latest cached change percentages, and 24h sparkline charts.
     */
    private static RemoteViews buildWidgetViews(Context context, boolean showLoading, String syncButtonText) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stocks_6x6);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Auto-restore backup from public storage if this is a fresh install after uninstalling
        restoreBackupFromDiskIfEmpty(context, prefs);

        // 1. Pending Intents
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

        // 2. Realtime Toggle Button Status
        boolean isRealtimeOn = prefs.getBoolean(KEY_REALTIME_ON, false);
        if (isRealtimeOn) {
            views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 ON (30s)");
            views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#10B981"));
        } else {
            views.setTextViewText(R.id.widget_toggle_realtime, "⚡ 실시간 OFF");
            views.setTextColor(R.id.widget_toggle_realtime, Color.parseColor("#94A3B8"));
        }

        // 3. Sync Button Text
        if (showLoading) {
            views.setTextViewText(R.id.widget_sync_btn, "⏳ 갱신 중...");
        } else if (syncButtonText != null) {
            views.setTextViewText(R.id.widget_sync_btn, syncButtonText);
        } else {
            String lastTime = prefs.getString(KEY_LAST_SYNC_TIME, null);
            if (lastTime != null) {
                views.setTextViewText(R.id.widget_sync_btn, "🔄 " + lastTime);
            } else {
                views.setTextViewText(R.id.widget_sync_btn, "🔄 동기화");
            }
        }

        // 4. Determine Weekend vs Weekday titles & keys
        boolean isWeekend = isWeekendSession();
        String name1, name2, name3, name4;
        String key1, key2, key3, key4;

        if (isWeekend) {
            name1 = "W 코스피200";
            name2 = "W 나스닥100";
            name3 = "W Tech 100";
            name4 = "W Oil (Crude)";
            key1 = "w_kr200";
            key2 = "w_nq100";
            key3 = "w_tech";
            key4 = "w_oil";
        } else {
            name1 = "코스피 야간선물";
            name2 = "나스닥 100";
            name3 = "S&P 500";
            name4 = "WTI 원유";
            key1 = "kmngt";
            key2 = "ndx";
            key3 = "spx";
            key4 = "wti";
        }

        views.setTextViewText(R.id.w_sub_1, name1);
        views.setTextViewText(R.id.w_sub_2, name2);
        views.setTextViewText(R.id.w_sub_3, name3);
        views.setTextViewText(R.id.w_sub_4, name4);

        // 5. Render 12 stocks with Cached Values & 24h Charts
        renderStockItem(views, R.id.w_item_1, R.id.w_chart_1, prefs, key1);
        renderStockItem(views, R.id.w_item_2, R.id.w_chart_2, prefs, key2);
        renderStockItem(views, R.id.w_item_3, R.id.w_chart_3, prefs, key3);
        renderStockItem(views, R.id.w_item_4, R.id.w_chart_4, prefs, key4);
        renderStockItem(views, R.id.w_item_5, R.id.w_chart_5, prefs, "skhx");
        renderStockItem(views, R.id.w_item_6, R.id.w_chart_6, prefs, "smsn");
        renderStockItem(views, R.id.w_item_7, R.id.w_chart_7, prefs, "sem");
        renderStockItem(views, R.id.w_item_8, R.id.w_chart_8, prefs, "mu");
        renderStockItem(views, R.id.w_item_9, R.id.w_chart_9, prefs, "pltr");
        renderStockItem(views, R.id.w_item_10, R.id.w_chart_10, prefs, "nvda");
        renderStockItem(views, R.id.w_item_11, R.id.w_chart_11, prefs, "meta");
        renderStockItem(views, R.id.w_item_12, R.id.w_chart_12, prefs, "tsla");

        return views;
    }

    private static void renderStockItem(RemoteViews views, int textId, int chartId, SharedPreferences prefs, String stockKey) {
        float cached = prefs.getFloat(PREF_CACHE_PREFIX + stockKey, Float.NaN);
        if (Float.isNaN(cached)) {
            views.setTextViewText(textId, "--");
            views.setTextColor(textId, Color.parseColor("#94A3B8"));
            return;
        }

        double chg = (double) cached;
        String sign = chg > 0 ? "+" : "";
        String text = String.format(Locale.US, "%s%.2f%%", sign, chg);
        int color = chg > 0 ? Color.parseColor("#FF3B30") : (chg < 0 ? Color.parseColor("#0A84FF") : Color.parseColor("#94A3B8"));
        views.setTextViewText(textId, text);
        views.setTextColor(textId, color);

        double[] history = getHistory(prefs, stockKey, chg);
        Bitmap chartBitmap = createSparklineBitmap(history, chg >= 0);
        views.setImageViewBitmap(chartId, chartBitmap);
    }

    /**
     * Performs parallel asynchronous network fetch for all indicators.
     * Guaranteed Stale-While-Revalidate: If any network call fails or times out,
     * the existing cached value is 100% PRESERVED. Never fall back to static dummy numbers!
     */
    private void syncWidgetData(Context context) {
        if (!isSyncInProgress.compareAndSet(false, true)) {
            // Already syncing, avoid duplicate queueing
            return;
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, StocksAppWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
        if (appWidgetIds == null || appWidgetIds.length == 0) {
            isSyncInProgress.set(false);
            return;
        }

        // 1. Immediately provide visual feedback: show "⏳ 갱신 중..." while keeping all current stock data intact!
        RemoteViews loadingViews = buildWidgetViews(context, true, null);
        appWidgetManager.updateAppWidget(appWidgetIds, loadingViews);

        // 2. Launch parallel worker thread
        networkPool.execute(() -> {
            try {
                long nowTs = System.currentTimeMillis();
                boolean isWeekend = isWeekendSession();
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();

                final double[] fxRateHolder = new double[]{ prefs.getFloat(PREF_CACHE_PREFIX + "fx_rate", 1346.0f) };
                final double[] semRefKrwHolder = new double[]{ prefs.getFloat(PREF_CACHE_PREFIX + "sem_ref_price", 944000.0f) };
                final double[] binanceLastPxHolder = new double[]{ Double.NaN };

                // Use CountDownLatch to coordinate parallel fetches
                int taskCount = isWeekend ? 4 : 6;
                CountDownLatch latch = new CountDownLatch(taskCount);

                if (isWeekend) {
                    // Task W1: Weekend Session
                    networkPool.execute(() -> {
                        try {
                            String wJson = fetchHttp("https://hlkr.co.kr/api/weekend-session?_t=" + nowTs);
                            if (wJson != null) {
                                JSONObject wObj = new JSONObject(wJson);
                                if (wObj.has("kospi")) {
                                    double val = wObj.getJSONObject("kospi").optDouble("changePct", Double.NaN);
                                    if (!Double.isNaN(val)) saveStock(editor, prefs, "w_kr200", val, nowTs);
                                }
                                if (wObj.has("nasdaq")) {
                                    double val = wObj.getJSONObject("nasdaq").optDouble("changePct", Double.NaN);
                                    if (!Double.isNaN(val)) saveStock(editor, prefs, "w_nq100", val, nowTs);
                                }
                            }
                        } catch (Exception ignored) {}
                        finally { latch.countDown(); }
                    });

                    // Task W2: IG Weekend Tech 100
                    networkPool.execute(() -> {
                        try {
                            String html = fetchHttp("https://www.ig.com/en/indices/markets-indices/weekend-us-tech-100-e1?_t=" + nowTs);
                            double val = parseIgChange(html);
                            if (!Double.isNaN(val)) saveStock(editor, prefs, "w_tech", val, nowTs);
                        } catch (Exception ignored) {}
                        finally { latch.countDown(); }
                    });

                    // Task W3: IG Weekend Oil
                    networkPool.execute(() -> {
                        try {
                            String html = fetchHttp("https://www.ig.com/en/indices/markets-indices/weekend-oil---us-crude?_t=" + nowTs);
                            double val = parseIgChange(html);
                            if (!Double.isNaN(val)) saveStock(editor, prefs, "w_oil", val, nowTs);
                        } catch (Exception ignored) {}
                        finally { latch.countDown(); }
                    });

                } else {
                    // Task D1: 코스피 야간선물
                    networkPool.execute(() -> {
                        try {
                            String kFutJson = fetchHttp("https://hlkr.co.kr/api/kospi-futures?_t=" + nowTs);
                            if (kFutJson != null) {
                                JSONObject kObj = new JSONObject(kFutJson);
                                if (kObj.has("kospiNgt")) {
                                    double val = kObj.getJSONObject("kospiNgt").optDouble("changePct", Double.NaN);
                                    if (!Double.isNaN(val)) saveStock(editor, prefs, "kmngt", val, nowTs);
                                }
                            }
                        } catch (Exception ignored) {}
                        finally { latch.countDown(); }
                    });

                    // Task D2: 나스닥 100 선물 (NQ=F) - with 24H 15m candle seeding
                    networkPool.execute(() -> {
                        try {
                            String nqJson = fetchHttp("https://query1.finance.yahoo.com/v8/finance/chart/NQ=F?interval=15m&range=1d&_t=" + nowTs);
                            double val = parseYahooChangeAndSeed(nqJson, "ndx", prefs, editor, nowTs);
                            if (!Double.isNaN(val)) {
                                saveStock(editor, prefs, "ndx", val, nowTs);
                            } else {
                                String marketHtml = fetchHttp("https://hlkr.co.kr/market?_t=" + nowTs);
                                double fbVal = parseMarketChange(marketHtml, "nasdaq");
                                if (!Double.isNaN(fbVal)) saveStock(editor, prefs, "ndx", fbVal, nowTs);
                            }
                        } catch (Exception ignored) {}
                        finally { latch.countDown(); }
                    });

                    // Task D3: S&P 500 선물 (ES=F) - with 24H 15m candle seeding
                    networkPool.execute(() -> {
                        try {
                            String esJson = fetchHttp("https://query1.finance.yahoo.com/v8/finance/chart/ES=F?interval=15m&range=1d&_t=" + nowTs);
                            double val = parseYahooChangeAndSeed(esJson, "spx", prefs, editor, nowTs);
                            if (!Double.isNaN(val)) {
                                saveStock(editor, prefs, "spx", val, nowTs);
                            } else {
                                String marketHtml = fetchHttp("https://hlkr.co.kr/market?_t=" + nowTs);
                                double fbVal = parseMarketChange(marketHtml, "sp500");
                                if (!Double.isNaN(fbVal)) saveStock(editor, prefs, "spx", fbVal, nowTs);
                            }
                        } catch (Exception ignored) {}
                        finally { latch.countDown(); }
                    });

                    // Task D4: WTI 원유 선물 (CL=F) - with 24H 15m candle seeding
                    networkPool.execute(() -> {
                        try {
                            String clJson = fetchHttp("https://query1.finance.yahoo.com/v8/finance/chart/CL=F?interval=15m&range=1d&_t=" + nowTs);
                            double val = parseYahooChangeAndSeed(clJson, "wti", prefs, editor, nowTs);
                            if (!Double.isNaN(val)) {
                                saveStock(editor, prefs, "wti", val, nowTs);
                            } else {
                                String macroJson = fetchHttp("https://hlkr.co.kr/api/dash-macro?_t=" + nowTs);
                                double fbVal = parseMacroChange(macroJson, "wti");
                                if (!Double.isNaN(fbVal)) saveStock(editor, prefs, "wti", fbVal, nowTs);
                            }
                        } catch (Exception ignored) {}
                        finally { latch.countDown(); }
                    });
                }

                // Task Common 1: 개별 주식 스냅샷 (SKHX, SMSN, MU, PLTR, NVDA, META, TSLA, SAMSUNGEM ref, fx.rate)
                networkPool.execute(() -> {
                    try {
                        String snapJson = fetchHttp("https://hlkr.co.kr/api/snapshot?_t=" + nowTs);
                        if (snapJson != null) {
                            JSONObject snapObj = new JSONObject(snapJson);
                            JSONObject fxObj = snapObj.optJSONObject("fx");
                            if (fxObj != null) {
                                double r = fxObj.optDouble("rate", Double.NaN);
                                if (!Double.isNaN(r) && r > 0) {
                                    fxRateHolder[0] = r;
                                    editor.putFloat(PREF_CACHE_PREFIX + "fx_rate", (float) r);
                                }
                            }

                            JSONArray assets = snapObj.optJSONArray("assets");
                            if (assets != null) {
                                for (int i = 0; i < assets.length(); i++) {
                                    JSONObject it = assets.getJSONObject(i);
                                    JSONObject assetObj = it.optJSONObject("asset");
                                    String sym = assetObj != null ? assetObj.optString("symbol") : it.optString("symbol");

                                    // Domestic stocks (SKHX, SMSN): Calculate against actual KRX close to avoid NXT aftermarket distortion (e.g. 0.64% vs 2~3%)
                                    double val = Double.NaN;
                                    JSONObject naverObj = it.optJSONObject("naver");
                                    double krxClose = naverObj != null ? naverObj.optDouble("krxClose", Double.NaN) : Double.NaN;
                                    double perpKrw = it.optDouble("perpKrw", Double.NaN);

                                    if (("SKHX".equals(sym) || "SMSN".equals(sym)) && !Double.isNaN(krxClose) && krxClose > 0 && !Double.isNaN(perpKrw) && perpKrw > 0) {
                                        val = Math.round(((perpKrw - krxClose) / krxClose) * 10000.0) / 100.0;
                                    } else {
                                        double prem = it.optDouble("premiumPct", Double.NaN);
                                        if (!Double.isNaN(prem)) {
                                            val = Math.round(prem * 100.0) / 100.0;
                                        }
                                    }

                                    if (!Double.isNaN(val)) {
                                        if ("SKHX".equals(sym)) saveStock(editor, prefs, "skhx", val, nowTs);
                                        else if ("SMSN".equals(sym)) saveStock(editor, prefs, "smsn", val, nowTs);
                                        else if ("MU".equals(sym)) saveStock(editor, prefs, "mu", val, nowTs);
                                        else if ("PLTR".equals(sym)) saveStock(editor, prefs, "pltr", val, nowTs);
                                        else if ("NVDA".equals(sym)) saveStock(editor, prefs, "nvda", val, nowTs);
                                        else if ("META".equals(sym)) saveStock(editor, prefs, "meta", val, nowTs);
                                        else if ("TSLA".equals(sym)) saveStock(editor, prefs, "tsla", val, nowTs);
                                    }
                                    if ("SAMSUNGEM".equals(sym)) {
                                        double ref = it.optDouble("refPriceKrw", Double.NaN);
                                        if (!Double.isNaN(ref) && ref > 0) {
                                            semRefKrwHolder[0] = ref;
                                            editor.putFloat(PREF_CACHE_PREFIX + "sem_ref_price", (float) ref);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    finally { latch.countDown(); }
                });

                // Task Common 2: 삼성전기 바이낸스 선물 시세 수신
                networkPool.execute(() -> {
                    try {
                        String binanceJson = fetchHttp("https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=SAMSUNGEMUSDT&_t=" + nowTs);
                        if (binanceJson != null) {
                            JSONObject bObj = new JSONObject(binanceJson);
                            double lastPx = bObj.optDouble("lastPrice", Double.NaN);
                            if (!Double.isNaN(lastPx) && lastPx > 0) {
                                binanceLastPxHolder[0] = lastPx;
                            }
                        }
                    } catch (Exception ignored) {}
                    finally { latch.countDown(); }
                });

                // Wait up to 5 seconds for all parallel network calls to complete
                latch.await(5000, TimeUnit.MILLISECONDS);

                // Calculate 삼성전기 (Binance USDT * Realtime FX Rate vs Reference KRW Price)
                if (!Double.isNaN(binanceLastPxHolder[0]) && semRefKrwHolder[0] > 0) {
                    double perpKrw = binanceLastPxHolder[0] * fxRateHolder[0];
                    double semChg = Math.round(((perpKrw - semRefKrwHolder[0]) / semRefKrwHolder[0]) * 10000.0) / 100.0;
                    saveStock(editor, prefs, "sem", semChg, nowTs);
                }

                // Commit updated cache
                String timeStr = new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date());
                editor.putString(KEY_LAST_SYNC_TIME, timeStr);
                editor.commit(); // Ensure synchronous write before UI render and backup

                // Save persistent backup to public storage
                saveBackupToDisk(context, prefs);

                // Update UI on main thread
                mainHandler.post(() -> {
                    try {
                        RemoteViews views = buildWidgetViews(context, false, "🔄 " + timeStr);
                        appWidgetManager.updateAppWidget(appWidgetIds, views);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        isSyncInProgress.set(false);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    try {
                        RemoteViews views = buildWidgetViews(context, false, null);
                        appWidgetManager.updateAppWidget(appWidgetIds, views);
                    } catch (Exception ignored) {}
                    finally {
                        isSyncInProgress.set(false);
                    }
                });
            }
        });
    }

    private static synchronized void saveStock(SharedPreferences.Editor editor, SharedPreferences prefs, String stockKey, double value, long nowTs) {
        editor.putFloat(PREF_CACHE_PREFIX + stockKey, (float) value);
        recordHistory(prefs, stockKey, value, nowTs);
    }

    /**
     * Parses Yahoo Chart JSON, extracts latest change %, and if history is empty,
     * seeds past 24 hours (15m intervals) from quote closes so charts appear instantly!
     */
    private static double parseYahooChangeAndSeed(String json, String stockKey, SharedPreferences prefs, SharedPreferences.Editor editor, long nowTs) {
        if (json == null) return Double.NaN;
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject chart = obj.optJSONObject("chart");
            if (chart != null) {
                JSONArray resArr = chart.optJSONArray("result");
                if (resArr != null && resArr.length() > 0) {
                    JSONObject res = resArr.getJSONObject(0);
                    JSONObject meta = res.optJSONObject("meta");
                    if (meta != null) {
                        double price = meta.optDouble("regularMarketPrice", Double.NaN);
                        double prevClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", Double.NaN));
                        double currentChange = Double.NaN;
                        if (!Double.isNaN(price) && !Double.isNaN(prevClose) && prevClose > 0) {
                            currentChange = Math.round(((price - prevClose) / prevClose) * 10000.0) / 100.0;
                        }

                        // Seed past 24h history from 15m candle closes if history has fewer than 5 points
                        String histKey = PREF_HISTORY_PREFIX + stockKey;
                        String currentHist = prefs.getString(histKey, "");
                        if (currentHist.isEmpty() || currentHist.split(";").length < 5) {
                            JSONArray timestamps = res.optJSONArray("timestamp");
                            JSONObject indicators = res.optJSONObject("indicators");
                            if (timestamps != null && indicators != null && !Double.isNaN(prevClose) && prevClose > 0) {
                                JSONArray quoteArr = indicators.optJSONArray("quote");
                                if (quoteArr != null && quoteArr.length() > 0) {
                                    JSONArray closeArr = quoteArr.getJSONObject(0).optJSONArray("close");
                                    if (closeArr != null && closeArr.length() == timestamps.length()) {
                                        StringBuilder sb = new StringBuilder();
                                        for (int i = 0; i < timestamps.length(); i++) {
                                            double cPx = closeArr.optDouble(i, Double.NaN);
                                            long tSec = timestamps.optLong(i, 0);
                                            if (!Double.isNaN(cPx) && tSec > 0) {
                                                long tMs = tSec * 1000L;
                                                if (nowTs - tMs < MAX_HISTORY_MS) {
                                                    double cPct = Math.round(((cPx - prevClose) / prevClose) * 10000.0) / 100.0;
                                                    if (sb.length() > 0) sb.append(";");
                                                    sb.append(tMs).append(":").append(String.format(Locale.US, "%.2f", cPct));
                                                }
                                            }
                                        }
                                        if (sb.length() > 0) {
                                            editor.putString(histKey, sb.toString());
                                            prefs.edit().putString(histKey, sb.toString()).apply();
                                        }
                                    }
                                }
                            }
                        }

                        return currentChange;
                    }
                }
            }
        } catch (Exception ignored) {}
        return Double.NaN;
    }

    private static double parseMarketChange(String html, String key) {
        if (html == null) return Double.NaN;
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"key\":\"" + key + "\",.*?\"changePct\":([+\\-]?[0-9.]+)");
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) {
                return Math.round(Double.parseDouble(m.group(1)) * 100.0) / 100.0;
            }
        } catch (Exception ignored) {}
        return Double.NaN;
    }

    private static double parseMacroChange(String json, String targetKey) {
        if (json == null) return Double.NaN;
        try {
            JSONObject mObj = new JSONObject(json);
            JSONArray items = mObj.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    if (targetKey.equals(it.optString("key"))) {
                        double val = it.optDouble("changePct", Double.NaN);
                        if (!Double.isNaN(val)) {
                            return Math.round(val * 100.0) / 100.0;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return Double.NaN;
    }

    private static double parseIgChange(String html) {
        if (html == null) return Double.NaN;
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("data-field=\"CPC\"[^>]*>([+\\-]?[0-9.]+)");
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) {
                return Math.round(Double.parseDouble(m.group(1)) * 100.0) / 100.0;
            }
        } catch (Exception ignored) {}
        return Double.NaN;
    }

    // 24H Local History Ring Buffer
    private static synchronized void recordHistory(SharedPreferences prefs, String key, double value, long nowTs) {
        if (Double.isNaN(value)) return;

        String histKey = PREF_HISTORY_PREFIX + key;
        String raw = prefs.getString(histKey, "");
        StringBuilder sb = new StringBuilder();

        if (!raw.isEmpty()) {
            String[] tokens = raw.split(";");
            for (String t : tokens) {
                String[] parts = t.split(":");
                if (parts.length == 2) {
                    try {
                        long pts = Long.parseLong(parts[0]);
                        if (nowTs - pts < MAX_HISTORY_MS) {
                            if (sb.length() > 0) sb.append(";");
                            sb.append(t);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (sb.length() > 0) sb.append(";");
        sb.append(nowTs).append(":").append(String.format(Locale.US, "%.2f", value));

        prefs.edit().putString(histKey, sb.toString()).apply();
    }

    private static double[] getHistory(SharedPreferences prefs, String key, double fallbackVal) {
        String histKey = PREF_HISTORY_PREFIX + key;
        String raw = prefs.getString(histKey, "");
        if (raw.isEmpty()) {
            return new double[]{ fallbackVal, fallbackVal };
        }
        String[] tokens = raw.split(";");
        List<Double> list = new ArrayList<>();
        for (String t : tokens) {
            String[] parts = t.split(":");
            if (parts.length == 2) {
                try {
                    list.add(Double.parseDouble(parts[1]));
                } catch (Exception ignored) {}
            }
        }
        if (list.isEmpty()) {
            return new double[]{ fallbackVal, fallbackVal };
        }
        if (list.size() == 1) {
            return new double[]{ list.get(0), list.get(0) };
        }
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    // In-Memory Canvas Sparkline Generator (0.001s ultra-lightweight)
    private static Bitmap createSparklineBitmap(double[] points, boolean isUp) {
        int width = 160;
        int height = 36;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        if (points == null || points.length < 2) {
            return bitmap;
        }

        double min = points[0];
        double max = points[0];
        for (double p : points) {
            if (p < min) min = p;
            if (p > max) max = p;
        }

        double range = max - min;
        if (range < 0.2) {
            min -= 0.1;
            max += 0.1;
            range = max - min;
        }

        int lineColor = isUp ? Color.parseColor("#FF453A") : Color.parseColor("#0A84FF");
        int gradientTop = isUp ? Color.argb(90, 255, 69, 58) : Color.argb(90, 10, 132, 255);
        int gradientBottom = Color.argb(0, 0, 0, 0);

        Path linePath = new Path();
        Path fillPath = new Path();

        float padding = 4f;
        float drawW = width - (padding * 2);
        float drawH = height - (padding * 2);

        float firstX = padding;
        float firstY = (float) (height - padding - ((points[0] - min) / range * drawH));
        linePath.moveTo(firstX, firstY);
        fillPath.moveTo(firstX, height);
        fillPath.lineTo(firstX, firstY);

        int count = points.length;
        for (int i = 1; i < count; i++) {
            float x = padding + ((float) i / (count - 1)) * drawW;
            float y = (float) (height - padding - ((points[i] - min) / range * drawH));

            float prevX = padding + ((float) (i - 1) / (count - 1)) * drawW;
            float prevY = (float) (height - padding - ((points[i - 1] - min) / range * drawH));
            float midX = (prevX + x) / 2;

            linePath.cubicTo(midX, prevY, midX, y, x, y);
            fillPath.cubicTo(midX, prevY, midX, y, x, y);
        }

        float lastX = padding + drawW;
        fillPath.lineTo(lastX, height);
        fillPath.close();

        // 1. Draw subtle gradient fill under trend curve
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setShader(new LinearGradient(0, 0, 0, height, gradientTop, gradientBottom, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);

        // 2. Draw smooth stroke curve
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(lineColor);
        linePaint.setStrokeWidth(3.0f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(linePath, linePaint);

        return bitmap;
    }

    private static String fetchHttp(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setUseCaches(false);
            conn.setDefaultUseCaches(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
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

    // =========================================================================
    // Persistent Public Storage Backup & Restore (Survives App Uninstall!)
    // =========================================================================

    private static File getBackupFile(Context context) {
        try {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl != null && (dl.exists() || dl.mkdirs())) {
                return new File(dl, BACKUP_FILE_NAME);
            }
        } catch (Exception ignored) {}
        return new File(context.getExternalFilesDir(null), BACKUP_FILE_NAME);
    }

    private static void saveBackupToDisk(Context context, SharedPreferences prefs) {
        networkPool.execute(() -> {
            try {
                File target = getBackupFile(context);
                if (target == null) return;

                JSONObject root = new JSONObject();
                root.put("version", 1);
                root.put("updatedAt", System.currentTimeMillis());

                JSONObject dataObj = new JSONObject();
                Map<String, ?> all = prefs.getAll();
                for (Map.Entry<String, ?> e : all.entrySet()) {
                    String k = e.getKey();
                    Object v = e.getValue();
                    if (v instanceof Float) {
                        dataObj.put(k, ((Float) v).doubleValue());
                    } else if (v instanceof String) {
                        dataObj.put(k, v);
                    } else if (v instanceof Boolean) {
                        dataObj.put(k, v);
                    }
                }
                root.put("data", dataObj);

                File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
                FileOutputStream fos = new FileOutputStream(tmp);
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.close();

                if (target.exists()) target.delete();
                tmp.renameTo(target);
            } catch (Exception ignored) {}
        });
    }

    private static synchronized void restoreBackupFromDiskIfEmpty(Context context, SharedPreferences prefs) {
        try {
            // Only restore if history is completely empty (e.g. freshly installed after uninstall)
            String testHistory = prefs.getString(PREF_HISTORY_PREFIX + "ndx", "");
            if (!testHistory.isEmpty()) return;

            File target = getBackupFile(context);
            if (target == null || !target.exists()) return;

            FileInputStream fis = new FileInputStream(target);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONObject dataObj = root.optJSONObject("data");
            if (dataObj != null) {
                SharedPreferences.Editor editor = prefs.edit();
                Iterator<String> keys = dataObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    Object v = dataObj.get(k);
                    if (v instanceof Double || v instanceof Integer) {
                        editor.putFloat(k, (float) dataObj.optDouble(k));
                    } else if (v instanceof String) {
                        editor.putString(k, (String) v);
                    } else if (v instanceof Boolean) {
                        editor.putBoolean(k, (Boolean) v);
                    }
                }
                editor.commit();
            }
        } catch (Exception ignored) {}
    }
}
