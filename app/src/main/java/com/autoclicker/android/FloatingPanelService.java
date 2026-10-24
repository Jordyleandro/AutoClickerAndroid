package com.autoclicker.android;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingPanelService extends Service {

    private WindowManager wm;
    private LinearLayout panel;
    private TextView marker;
    private TextView coordinatesLabel;

    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams markerParams;

    private int markerSize = 48;
    private int clicksPerSecond = 10;

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "Permite mostrar sobre otras aplicaciones",
                    Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelParams = new WindowManager.LayoutParams(
                300,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = 20;
        panelParams.y = 120;

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(12, 12, 12, 12);
        panel.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("AutoClickerNuevo");
        title.setTextColor(Color.BLACK);
        title.setTextSize(18);
        panel.addView(title);

        coordinatesLabel = new TextView(this);
        coordinatesLabel.setTextColor(Color.BLUE);
        coordinatesLabel.setTextSize(16);
        coordinatesLabel.setText("X: -- | Y: --");
        panel.addView(coordinatesLabel);

        Button start = new Button(this);
        start.setText("INICIAR CLICS");
        start.setOnClickListener(v -> {
            AutoClickService s = AutoClickService.getInstance();

            if (s == null) {
                Toast.makeText(this,
                        "Activa AutoClicker en Accesibilidad",
                        Toast.LENGTH_LONG).show();

                Intent intent = new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }

            updateClickPosition();
            s.setInterval(1000L / clicksPerSecond);
            s.startClicks();

            Toast.makeText(this,
                    "Clics iniciados",
                    Toast.LENGTH_SHORT).show();
        });
        panel.addView(start);

        Button stop = new Button(this);
        stop.setText("DETENER CLICS");
        stop.setOnClickListener(v -> {
            AutoClickService s = AutoClickService.getInstance();
            if (s != null) {
                s.stopClicks();
            }
            Toast.makeText(this,
                    "Clics detenidos",
                    Toast.LENGTH_SHORT).show();
        });
        panel.addView(stop);

        TextView speedLabel = new TextView(this);
        speedLabel.setTextColor(Color.BLACK);
        speedLabel.setText("Velocidad: 10 clics/seg");
        panel.addView(speedLabel);

        SeekBar speed = new SeekBar(this);
        speed.setMax(19);
        speed.setProgress(9);
        speed.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar b, int p, boolean user) {
                clicksPerSecond = p + 1;
                speedLabel.setText(
                        "Velocidad: " + clicksPerSecond + " clics/seg");

                AutoClickService s = AutoClickService.getInstance();
                if (s != null) {
                    s.setInterval(1000L / clicksPerSecond);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {}
        });
        panel.addView(speed);

        TextView sizeLabel = new TextView(this);
        sizeLabel.setTextColor(Color.BLACK);
        sizeLabel.setText("Tamaño del punto");
        panel.addView(sizeLabel);

        SeekBar size = new SeekBar(this);
        size.setMax(100);
        size.setProgress(28);
        size.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar b, int p, boolean user) {
                markerSize = p + 20;

                if (marker != null && markerParams != null) {
                    markerParams.width = markerSize;
                    markerParams.height = markerSize;
                    marker.setTextSize(markerSize / 2f);
                    wm.updateViewLayout(marker, markerParams);
                    updateClickPosition();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {}
        });
        panel.addView(size);

        Button close = new Button(this);
        close.setText("CERRAR PANEL");
        close.setOnClickListener(v -> stopSelf());
        panel.addView(close);

        wm.addView(panel, panelParams);
        createMarker(type);
        makeDraggable(panel, panelParams);
    }

    private void createMarker(int type) {
        marker = new TextView(this);
        marker.setText("●");
        marker.setTextSize(markerSize / 2f);
        marker.setGravity(Gravity.CENTER);
        marker.setTextColor(Color.RED);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22FF0000);
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(2, Color.RED);
        marker.setBackground(bg);

        markerParams = new WindowManager.LayoutParams(
                markerSize,
                markerSize,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        markerParams.gravity = Gravity.TOP | Gravity.START;
        markerParams.x = 300;
        markerParams.y = 500;

        wm.addView(marker, markerParams);
        makeDraggable(marker, markerParams);
        updateClickPosition();
    }

    private void updateClickPosition() {
        if (marker == null || markerParams == null) {
            return;
        }

        int x = markerParams.x + markerSize / 2;
        int y = markerParams.y + markerSize / 2;

        coordinatesLabel.setText("X: " + x + " | Y: " + y);

        AutoClickService s = AutoClickService.getInstance();
        if (s != null) {
            s.setPosition(x, y);
        }
    }

    private void makeDraggable(
            View view, WindowManager.LayoutParams params) {

        view.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY, originalX, originalY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = (int) event.getRawX();
                    startY = (int) event.getRawY();
                    originalX = params.x;
                    originalY = params.y;
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = originalX
                            + (int) event.getRawX() - startX;
                    params.y = originalY
                            + (int) event.getRawY() - startY;

                    wm.updateViewLayout(view, params);

                    if (view == marker) {
                        updateClickPosition();
                    }
                    return true;
                }

                return event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL;
            }
        });
    }

    @Override
    public void onDestroy() {
        AutoClickService s = AutoClickService.getInstance();
        if (s != null) {
            s.stopClicks();
        }

        if (wm != null) {
            if (panel != null && panel.isAttachedToWindow()) {
                wm.removeView(panel);
            }
            if (marker != null && marker.isAttachedToWindow()) {
                wm.removeView(marker);
            }
        }

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
