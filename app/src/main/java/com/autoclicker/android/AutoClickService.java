
package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class AutoClickService extends AccessibilityService {

    private static final String TAG = "AutoClicker";

    private static AutoClickService instance;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private int clickX = 500;
    private int clickY = 500;
    private long interval = 200;

    private boolean clicking = false;
    private boolean gesturePending = false;

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

    public void setPosition(int x, int y) {
        clickX = Math.max(1, x);
        clickY = Math.max(1, y);
    }

    public void setInterval(long milliseconds) {
        interval = Math.max(100, milliseconds);
    }

    public void startClicks() {
        if (instance != this) {
            Log.e(TAG, "La instancia del servicio no coincide");
            return;
        }

        if (clicking) {
            Log.i(TAG, "Los clics ya están iniciados");
            return;
        }

        clicking = true;
        gesturePending = false;

        Log.i(TAG, "Inicio: X=" + clickX
                + ", Y=" + clickY
                + ", intervalo=" + interval);

        handler.post(clickRunnable);
    }

    public void stopClicks() {
        clicking = false;
        handler.removeCallbacks(clickRunnable);
        Log.i(TAG, "Clics detenidos");
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!clicking || gesturePending) {
                return;
            }

            Path path = new Path();
            path.moveTo(clickX, clickY);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(
                            path, 0, 100);

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
                            Log.i(TAG, "GESTO COMPLETADO en X="
                                    + clickX + ", Y=" + clickY);
                            scheduleNext();
                        }

                        @Override
                        public void onCancelled(
                                GestureDescription description) {
                            gesturePending = false;
                            Log.e(TAG, "GESTO CANCELADO");
                            scheduleNext();
                        }
                    },
                    handler
            );

            if (!accepted) {
                gesturePending = false;
                Log.e(TAG, "ANDROID RECHAZÓ EL GESTO");
                scheduleNext();
            }
        }
    };

    private void scheduleNext() {
        if (clicking) {
            handler.postDelayed(clickRunnable, interval);
        }
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

        if (instance == this) {
            instance = null;
        }

        super.onDestroy();
    }
}
