package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;
import android.os.Handler;
import android.os.Looper;

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
                                            path, 0, 50))
                            .build();

            dispatchGesture(gesture, null, null);
            handler.postDelayed(this, interval);
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
