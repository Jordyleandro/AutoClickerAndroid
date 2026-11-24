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
    private static final long MIN_TAP_GAP_MS = 50L;
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

    // El intervalo representa el ciclo completo, no cada toque individual.
    public void setInterval(long milliseconds) {
        interval = Math.max(50L, milliseconds);
    }

    public void setClicksPerPoint(int count) {
        clicksPerPoint = Math.max(1, Math.min(6, count));
        if (clicking && !gesturePending) tapsRemaining = clicksPerPoint;
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
        // Desactivar primero el bucle para que ningún callback pueda reanudarlo.
        clicking = false;
        tapsRemaining = 0;
        handler.removeCallbacks(clickRunnable);
        Log.i(TAG, "PARADA DE EMERGENCIA: clics detenidos");
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!clicking || gesturePending) return;
            if (tapsRemaining <= 0) tapsRemaining = clicksPerPoint;

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

                    if (clicking) {
                        long slot = interval / Math.max(1, clicksPerPoint);
                        long pause = Math.max(MIN_TAP_GAP_MS,
                                slot - GESTURE_DURATION_MS);
                        // Cada toque se envía como un gesto independiente.
                        handler.postDelayed(clickRunnable, pause);
                    }
                }

                @Override
                public void onCancelled(GestureDescription description) {
                    gesturePending = false;
                    if (clicking) handler.postDelayed(clickRunnable, 250L);
                }
            }, handler);

            if (!accepted) {
                gesturePending = false;
                Log.w(TAG, "Android rechazó el toque; reintento pausado");
                if (clicking) handler.postDelayed(this, 250L);
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