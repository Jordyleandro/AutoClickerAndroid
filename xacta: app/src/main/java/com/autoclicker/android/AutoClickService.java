package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class AutoClickService extends AccessibilityService {
    private static AutoClickService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private int x = 300;
    private int y = 500;
    private long interval = 100;

    private final Runnable task = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            clickOnce();
            handler.postDelayed(this, interval);
        }
    };

    public static AutoClickService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    public void setPosition(int px, int py) {
        x = px;
        y = py;
    }

    public void setInterval(long ms) {
        interval = Math.max(50, ms);
    }

    public void startClicks() {
        if (running) return;
        running = true;
        handler.post(task);
    }

    public void stopClicks() {
        running = false;
        handler.removeCallbacks(task);
    }

    private void clickOnce() {
        if (Build.VERSION.SDK_INT < 24) return;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription gesture =
            new GestureDescription.Builder()
                .addStroke(
                    new GestureDescription.StrokeDescription(path, 0, 50)
                )
                .build();

        dispatchGesture(gesture, null, null);
    }

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
