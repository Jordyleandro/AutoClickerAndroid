package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class AutoClickService extends AccessibilityService {

    private static final String TAG = "AutoClicker";

    private static final long GESTURE_DURATION_MS = 100L;
    private static final float MOVE_DISTANCE = 1.0f;

    private static AutoClickService instance;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private int clickX = 500;
    private int clickY = 500;

    private long interval = 1L;

    private boolean clicking = false;
    private boolean gesturePending = false;

    private long completedClicks = 0L;
    private long cancelledGestures = 0L;

    private long rateWindowStart = 0L;
    private int clicksInWindow = 0;
    private int clicksPerSecond = 0;

    private ToneGenerator toneGenerator;

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

        try {
            toneGenerator = new ToneGenerator(
                    AudioManager.STREAM_MUSIC,
                    70
            );
        } catch (RuntimeException e) {
            Log.e(TAG, "Error iniciando sonido", e);
        }

        Log.i(TAG, "Servicio conectado");

        Toast.makeText(
                this,
                "AutoClicker conectado",
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
        cancelledGestures = 0L;
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

    public void startClicks() {
        if (instance != this || clicking) {
            return;
        }

        resetStats();

        clicking = true;
        gesturePending = false;

        Log.i(
                TAG,
                "Inicio: X=" + clickX
                        + " Y=" + clickY
                        + " intervalo=" + interval
        );

        handler.removeCallbacks(clickRunnable);
        handler.post(clickRunnable);
    }

    public void stopClicks() {
        clicking = false;
        handler.removeCallbacks(clickRunnable);

        Log.i(
                TAG,
                "Detenido. Completados="
                        + completedClicks
                        + " Cancelados="
                        + cancelledGestures
        );
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {

            if (!clicking || gesturePending) {
                return;
            }

            final int x = clickX;
            final int y = clickY;

            Path path = new Path();

            path.moveTo(x, y);
            path.lineTo(
                    x + MOVE_DISTANCE,
                    y + MOVE_DISTANCE
            );

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(
                            path,
                            0L,
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

                            updateClickRate();
                            playClickSound();
                            notifyStats();

                            Log.d(
                                    TAG,
                                    "Gesto completado en "
                                            + x + "," + y
                            );

                            scheduleNext();
                        }

                        @Override
                        public void onCancelled(
                                GestureDescription description) {

                            gesturePending = false;
                            cancelledGestures++;

                            Log.w(
                                    TAG,
                                    "Gesto cancelado. Total="
                                            + cancelledGestures
                            );

                            scheduleNext();
                        }
                    },
                    handler
            );

            if (!accepted) {
                gesturePending = false;

                Log.e(
                        TAG,
                        "Android rechazó dispatchGesture"
                );

                scheduleNext();
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

    private void playClickSound() {

        if (toneGenerator == null) {
            return;
        }

        try {
            toneGenerator.startTone(
                    ToneGenerator.TONE_PROP_BEEP,
                    30
            );
        } catch (RuntimeException e) {
            Log.w(TAG, "Error de sonido", e);
        }
    }

    private void scheduleNext() {

        if (clicking) {
            handler.postDelayed(
                    clickRunnable,
                    interval
            );
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No se necesitan eventos.
    }

    @Override
    public void onInterrupt() {
        stopClicks();
    }

    @Override
    public void onDestroy() {

        stopClicks();

        statsListener = null;

        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }

        if (instance == this) {
            instance = null;
        }

        super.onDestroy();
    }
}
