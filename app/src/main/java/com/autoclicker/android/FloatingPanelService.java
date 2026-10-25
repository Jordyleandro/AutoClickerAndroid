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
    private TextView totalClicksLabel;
    private TextView actualSpeedLabel;

    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams markerParams;

    private int markerSize = 48;
    private int clicksPerSecond = 10;

    private static final int MAX_CLICKS_PER_SECOND = 1500;
    private static final int MIN_INTERVAL_MS = 1;

    private final AutoClickService.StatsListener statsListener =
            (total, rate) -> {
                if (totalClicksLabel != null) {
                    totalClicksLabel.setText(
                            "Clics completados: " + total
                    );
                }

                if (actualSpeedLabel != null) {
                    actualSpeedLabel.setText(
                            "Velocidad actual: " + rate
                                    + " clics/seg"
                    );
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                    this,
                    "Permite mostrar sobre otras aplicaciones",
                    Toast.LENGTH_LONG
            ).show();
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
        title.setText("Scg-92 autoclikerbb");
        title.setTextColor(Color.BLACK);
        title.setTextSize(18);
        panel.addView(title);

        coordinatesLabel = new TextView(this);
        coordinatesLabel.setTextColor(Color.BLUE);
        coordinatesLabel.setTextSize(16);
        coordinatesLabel.setText("X: -- | Y: --");
        panel.addView(coordinatesLabel);

        totalClicksLabel = new TextView(this);
        totalClicksLabel.setTextColor(Color.rgb(0, 120, 0));
        totalClicksLabel.setTextSize(18);
        totalClicksLabel.setText("Clics completados: 0");
        panel.addView(totalClicksLabel);

        actualSpeedLabel = new TextView(this);
        actualSpeedLabel.setTextColor(Color.BLACK);
        actualSpeedLabel.setTextSize(16);
        actualSpeedLabel.setText("Velocidad actual: 0 clics/seg");
        panel.addView(actualSpeedLabel);

        AutoClickService existingService =
                AutoClickService.getInstance();

        if (existingService != null) {
            existingService.setStatsListener(statsListener);
        }

        Button start = new Button(this);
        start.setText("INICIAR CLICS");

        start.setOnClickListener(v -> {
            AutoClickService s = AutoClickService.getInstance();

            if (s == null) {
                Toast.makeText(
                        this,
                        "Activa AutoClicker en Accesibilidad",
                        Toast.LENGTH_LONG
                ).show();

                Intent intent = new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }

            updateClickPosition();
            s.setStatsListener(statsListener);

            s.setInterval(
                    Math.max(
                            MIN_INTERVAL_MS,
                            1000L / clicksPerSecond
                    )
            );

            // Reinicia el contador al iniciar.
            s.startClicks();

            Toast.makeText(
                    this,
                    "Clics iniciados desde cero",
                    Toast.LENGTH_SHORT
            ).show();
        });

        panel.addView(start);

        Button stop = new Button(this);
        stop.setText("DETENER CLICS");

        stop.setOnClickListener(v -> {
            AutoClickService s = AutoClickService.getInstance();

            if (s != null) {
                s.stopClicks();
            }

            Toast.makeText(
                    this,
                    "Clics detenidos",
                    Toast.LENGTH_SHORT
            ).show();
        });

        panel.addView(stop);

        TextView speedLabel = new TextView(this);
        speedLabel.setTextColor(Color.BLACK);
        speedLabel.setText("Velocidad configurada: 10 clics/seg");
        panel.addView(speedLabel);

        SeekBar speed = new SeekBar(this);
        speed.setMax(MAX_CLICKS_PER_SECOND - 1);
        speed.setProgress(clicksPerSecond - 1);

        speed.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(
                    SeekBar b,
                    int progress,
                    boolean fromUser
            ) {
                clicksPerSecond = progress + 1;

                speedLabel.setText(
                        "Velocidad configurada: "
                                + clicksPerSecond
                                + " clics/seg"
                );

                AutoClickService s =
                        AutoClickService.getInstance();

                if (s != null) {
                    s.setInterval(
                            Math.max(
                                    MIN_INTERVAL_MS,
                                    1000L / clicksPerSecond
                            )
                    );
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar b) {
            }
        });

        panel.addView(speed);

        TextView sizeLabel = new TextView(this);
        sizeLabel.setTextColor(Color.BLACK);
        sizeLabel.setText("Tamaño del anillo");
        panel.addView(sizeLabel);

        SeekBar size = new SeekBar(this);
        size.setMax(100);
        size.setProgress(28);

        size.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(
                    SeekBar b,
                    int progress,
                    boolean fromUser
            ) {
                markerSize = progress + 20;

                if (marker != null && markerParams != null) {
                    markerParams.width = markerSize;
                    markerParams.height = markerSize;

                    wm.updateViewLayout(marker, markerParams);
                    updateClickPosition();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar b) {
            }
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
        marker.setText("");
        marker.setGravity(Gravity.CENTER);

        // Anillo amarillo con centro transparente.
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(4, Color.YELLOW);

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

        if (coordinatesLabel != null) {
            coordinatesLabel.setText(
                    "X: " + x + " | Y: " + y
            );
        }

        AutoClickService s = AutoClickService.getInstance();

        if (s != null) {
            s.setPosition(x, y);
        }
    }

    private void makeDraggable(
            View view,
            WindowManager.LayoutParams params
    ) {
        view.setOnTouchListener(new View.OnTouchListener() {

            private int startX;
            private int startY;
            private int originalX;
            private int originalY;

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
                        || event.getAction()
                        == MotionEvent.ACTION_CANCEL;
            }
        });
    }

    @Override
    public void onDestroy() {
        AutoClickService s = AutoClickService.getInstance();

        if (s != null) {
            s.stopClicks();
            s.setStatsListener(null);
        }

        if (wm != null) {
            if (panel != null && panel.isAttachedToWindow()) {
                wm.removeView(panel);
            }

            if (marker != null && marker.isAttachedToWindow()) {
                wm.removeView(marker);
            }
        }

        panel = null;
        marker = null;
        totalClicksLabel = null;
        actualSpeedLabel = null;

        super.onDestroy();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int id
    ) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
