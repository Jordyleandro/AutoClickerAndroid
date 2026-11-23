package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AutoClickService extends AccessibilityService {
    private static final String TAG = "AutoClicker";
    private static final long GESTURE_DURATION_MS = 10L;
    private static final long MIN_TAP_GAP_MS = 12L;
    private static AutoClickService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int clickX = 500;
    private int clickY = 500;
    private long interval = 12L;
    private int clicksPerPoint = 1;
    private int tapsRemaining = 0;
    private boolean clicking = false;
    private boolean gesturePending = false;

    private long completedClicks = 0L;
    private long rateWindowStart = 0L;
    private int clicksInWindow = 0;
    private int clicksPerSecond = 0;

    public interface StatsListener {
        void onClickStatsUpdated(long total, int clicksPerSecond);
    }

    private StatsListener statsListener;

    public static AutoClickService getInstance() { return instance; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Servicio de accesibilidad conectado");
    }

    public void setStatsListener(StatsListener listener) {
        statsListener = listener;
        notifyStats();
    }

    public long getCompletedClicks() { return completedClicks; }
    public int getClicksPerSecond() { return clicksPerSecond; }

    public void resetStats() {
        completedClicks = 0L;
        clicksPerSecond = 0;
        clicksInWindow = 0;
        rateWindowStart = SystemClock.elapsedRealtime();
        notifyStats();
    }

    private void notifyStats() {
        if (statsListener != null) {
            statsListener.onClickStatsUpdated(completedClicks, clicksPerSecond);
        }
    }

    public void setPosition(int x, int y) {
        clickX = Math.max(1, x);
        clickY = Math.max(1, y);
    }

    // Intervalo total de un ciclo: se reparte entre 1, 2 o 3 toques reales.
    public void setInterval(long milliseconds) {
        interval = Math.max(MIN_TAP_GAP_MS, milliseconds);
    }

    public void setClicksPerPoint(int count) {
        clicksPerPoint = Math.max(1, Math.min(3, count));
        if (clicking && !gesturePending) {
            tapsRemaining = clicksPerPoint;
        }
    }

    public void startClicks() {
        if (instance != this || clicking) return;
        resetStats();
        clicking = true;
        gesturePending = false;
        tapsRemaining = clicksPerPoint;
        handler.removeCallbacks(clickRunnable);
        handler.post(clickRunnable);
    }

    public void stopClicks() {
        clicking = false;
        tapsRemaining = 0;
        handler.removeCallbacks(clickRunnable);
        Log.i(TAG, "Clics detenidos");
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!clicking || gesturePending) return;

            if (tapsRemaining <= 0) {
                tapsRemaining = clicksPerPoint;
            }

            Path path = new Path();
            path.moveTo(clickX, clickY);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(
                            path, 0L, GESTURE_DURATION_MS))
                    .build();

            gesturePending = true;
            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription description) {
                    gesturePending = false;
                    completedClicks++;
                    updateClickRate();
                    notifyStats();

                    tapsRemaining--;
                    if (!clicking) return;

                    if (tapsRemaining > 0) {
                        long tapGap = Math.max(MIN_TAP_GAP_MS,
                                interval / Math.max(1, clicksPerPoint));
                        handler.postDelayed(clickRunnable, tapGap);
                    } else {
                        handler.post(clickRunnable);
                    }
                }

                @Override
                public void onCancelled(GestureDescription description) {
                    gesturePending = false;
                    if (clicking) handler.postDelayed(clickRunnable, MIN_TAP_GAP_MS);
                }
            }, handler);

            if (!accepted) {
                gesturePending = false;
                Log.w(TAG, "Android rechazó el toque; se reintentará con pausa");
                if (clicking) handler.postDelayed(this, 50L);
            }
        }
    };

    private void updateClickRate() {
        long now = SystemClock.elapsedRealtime();
        if (rateWindowStart == 0L) rateWindowStart = now;
        clicksInWindow++;
        long elapsed = now - rateWindowStart;
        if (elapsed >= 1000L) {
            clicksPerSecond = (int) Math.round(clicksInWindow * 1000.0 / elapsed);
            clicksInWindow = 0;
            rateWindowStart = now;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { stopClicks(); }

    @Override
    public void onDestroy() {
        stopClicks();
        statsListener = null;
        if (instance == this) instance = null;
        super.onDestroy();
    }
}