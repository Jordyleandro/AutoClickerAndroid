package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class AutoClickService extends AccessibilityService {

    private static final String TAG = "AutoClicker";

    private static final long GESTURE_DURATION_MS = 20L;
    private static final long REPEAT_GAP_MS = 100L;

    private static AutoClickService instance;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private int clickX = 500;
    private int clickY = 500;

    private long interval = 1L;
    private int clicksPerPoint = 1;
    private int clicksRemainingInBurst = 0;
    private int configuredBurstSize = 1;
    private int failedGestureRetries = 0;

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

    public static AutoClickService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        Log.i(TAG, "Servicio de accesibilidad conectado");

        Toast.makeText(
                this,
                "Servicio AutoClicker conectado",
                Toast.LENGTH_SHORT
        ).show();
    }

    public void setStatsListener(StatsListener listener) {
        statsListener = listener;
        notifyStats();
    }

    public long getCompletedClicks() {
        return completedClicks;
    }

    public int getClicksPerSecond() {
        return clicksPerSecond;
    }

    public void resetStats() {
        completedClicks = 0L;
        clicksPerSecond = 0;
        clicksInWindow = 0;
        rateWindowStart = SystemClock.elapsedRealtime();
        notifyStats();
    }

    private void notifyStats() {
        if (statsListener != null) {
            statsListener.onClickStatsUpdated(
                    completedClicks,
                    clicksPerSecond
            );
        }
    }

    public void setPosition(int x, int y) {
        clickX = Math.max(1, x);
        clickY = Math.max(1, y);
    }

    public void setInterval(long milliseconds) {
        interval = Math.max(1L, milliseconds);
    }

    public void setClicksPerPoint(int count) {
        clicksPerPoint = Math.max(1, Math.min(3, count));
        if (!clicking) {
            clicksRemainingInBurst = 0;
        }
    }

    public void startClicks() {
        if (instance != this || clicking) {
            return;
        }

        resetStats();

        clicking = true;
        gesturePending = false;
        clicksRemainingInBurst = 0;
        configuredBurstSize = clicksPerPoint;
        failedGestureRetries = 0;

        Log.i(
                TAG,
                "Clics iniciados: X=" + clickX
                        + ", Y=" + clickY
                        + ", intervalo=" + interval + " ms"
        );

        handler.removeCallbacks(clickRunnable);
        handler.post(clickRunnable);
    }

    public void stopClicks() {
        clicking = false;
        clicksRemainingInBurst = 0;
        handler.removeCallbacks(clickRunnable);

        Log.i(TAG, "Clics detenidos");
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!clicking || gesturePending) {
                return;
            }

            if (clicksRemainingInBurst <= 0) {
                configuredBurstSize = clicksPerPoint;
                clicksRemainingInBurst = configuredBurstSize;
            }

            Path path = new Path();
            path.moveTo(clickX, clickY);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(
                            path,
                            0,
                            GESTURE_DURATION_MS
                    );

            GestureDescription gesture =
                    new GestureDescription.Builder()
                            .addStroke(stroke)
                            .build();

            gesturePending = true;

            boolean accepted = dispatchGesture(
                    gesture,
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(
                                GestureDescription description) {

                            gesturePending = false;

                            completedClicks++;
                            failedGestureRetries = 0;
                            updateClickRate();
                            notifyStats();

                            clicksRemainingInBurst--;
                            if (clicking && clicksRemainingInBurst > 0) {
                                handler.postDelayed(clickRunnable, Math.max(interval, REPEAT_GAP_MS));
                            } else {
                                clicksRemainingInBurst = 0;
                                scheduleNext();
                            }
                        }

                        @Override
                        public void onCancelled(
                                GestureDescription description) {

                            gesturePending = false;
                            failedGestureRetries++;
                            Log.w(TAG, "Android canceló un gesto; intento " + failedGestureRetries);
                            if (clicking && failedGestureRetries <= 2) {
                                handler.postDelayed(clickRunnable, REPEAT_GAP_MS);
                            } else {
                                clicksRemainingInBurst = 0;
                                failedGestureRetries = 0;
                                scheduleNext();
                            }
                        }
                    },
                    handler
            );

            if (!accepted) {
                gesturePending = false;
                failedGestureRetries++;
                Log.w(TAG, "Android rechazó un gesto; intento " + failedGestureRetries);
                if (clicking && failedGestureRetries <= 2) {
                    handler.postDelayed(clickRunnable, REPEAT_GAP_MS);
                } else {
                    clicksRemainingInBurst = 0;
                    failedGestureRetries = 0;
                    scheduleNext();
                }
            }
        }
    };

    private void updateClickRate() {
        long now = SystemClock.elapsedRealtime();

        if (rateWindowStart == 0L) {
            rateWindowStart = now;
        }

        clicksInWindow++;

        long elapsed = now - rateWindowStart;

        if (elapsed >= 1000L) {
            clicksPerSecond = (int) Math.round(
                    clicksInWindow * 1000.0 / elapsed
            );

            clicksInWindow = 0;
            rateWindowStart = now;
        }
    }

    private void scheduleNext() {
        if (clicking) {
            handler.postDelayed(clickRunnable, interval);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No se necesitan eventos para programar los gestos.
    }

    @Override
    public void onInterrupt() {
        stopClicks();
    }

    @Override
    public void onDestroy() {
        stopClicks();

        statsListener = null;

        if (instance == this) {
            instance = null;
        }

        super.onDestroy();
    }
}
