package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class AutoClickService extends AccessibilityService {

    private static AutoClickService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int clickX = 500;
    private int clickY = 500;
    private long interval = 100;
    private boolean clicking = false;

    public static AutoClickService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    public void setPosition(int x, int y) {
        clickX = x;
        clickY = y;
    }

    public void setInterval(long milliseconds) {
        interval = Math.max(1, milliseconds);
    }

    public void startClicks() {
        if (clicking) return;
        clicking = true;
        handler.post(clickRunnable);
    }

    public void stopClicks() {
        clicking = false;
        handler.removeCallbacks(clickRunnable);
    }

    private void scheduleNext() {
        if (clicking) {
            handler.postDelayed(
                clickRunnable, Math.max(1, interval - 10)
            );
        }
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!clicking) return;

            Path path = new Path();
            path.moveTo(clickX, clickY);

            GestureDescription gesture =
                new GestureDescription.Builder()
                    .addStroke(
                        new GestureDescription.StrokeDescription(
                            path, 0, 10
                        )
                    )
                    .build();

            boolean accepted = dispatchGesture(
                gesture,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(
                        GestureDescription gestureDescription
                    ) {
                        scheduleNext();
                    }

                    @Override
                    public void onCancelled(
                        GestureDescription gestureDescription
                    ) {
                        scheduleNext();
                    }
                },
                null
            );

            if (!accepted && clicking) {
                handler.postDelayed(this, interval);
            }
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        stopClicks();
    }

    @Override
    public void onDestroy() {
        stopClicks();
        instance = null;
        super.onDestroy();
    }
    }
