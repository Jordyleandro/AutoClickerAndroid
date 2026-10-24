package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class AutoClickService extends AccessibilityService {

    private static AutoClickService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int clickX = 500;
    private int clickY = 500;
    private long interval = 100;
    private boolean clicking = false;
    private boolean gesturePending = false;

    public static AutoClickService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Toast.makeText(this, "AutoClicker conectado", Toast.LENGTH_SHORT).show();
    }

    public void setPosition(int x, int y) {
        clickX = x;
        clickY = y;
    }

    public void setInterval(long milliseconds) {
        interval = Math.max(50, milliseconds);
    }

    public void startClicks() {
        if (clicking) return;

        if (instance == null) {
            Toast.makeText(this, "Activa el servicio de accesibilidad", Toast.LENGTH_LONG).show();
            return;
        }

        clicking = true;
        handler.post(clickRunnable);
    }

    public void stopClicks() {
        clicking = false;
        gesturePending = false;
        handler.removeCallbacks(clickRunnable);
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!clicking || gesturePending) return;

            Path path = new Path();
            path.moveTo(clickX, clickY);

            GestureDescription gesture =
                    new GestureDescription.Builder()
                            .addStroke(new GestureDescription.StrokeDescription(
                                    path, 0, 50))
                            .build();

            gesturePending = true;

            boolean accepted = dispatchGesture(
                    gesture,
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription description) {
                            gesturePending = false;
                            if (clicking) {
                                handler.postDelayed(clickRunnable, interval);
                            }
                        }

                        @Override
                        public void onCancelled(GestureDescription description) {
                            gesturePending = false;
                            if (clicking) {
                                handler.postDelayed(clickRunnable, interval);
                            }
                        }
                    },
                    null
            );

            if (!accepted) {
                gesturePending = false;
                if (clicking) {
                    handler.postDelayed(this, interval);
                }
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
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
