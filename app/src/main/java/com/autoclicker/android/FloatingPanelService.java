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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingPanelService extends Service {

    private WindowManager wm;
    private LinearLayout panel;
    private LinearLayout content;
    private TextView marker;
    private TextView totalClicksLabel;
    private TextView actualSpeedLabel;
    private TextView configuredSpeedLabel;

    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams markerParams;

    private int markerSize = 48;
    private int clicksPerSecond = 10;

    private int panelWidth = 300;
    private int panelHeight = 440;

    private int screenWidth;
    private int screenHeight;

    private static final int MIN_PANEL_WIDTH = 200;
    private static final int MIN_PANEL_HEIGHT = 180;
    private static final int MAX_CLICKS_PER_SECOND = 1500;
    private static final int MIN_CLICKS_PER_SECOND = 1;
    private static final int MIN_INTERVAL_MS = 1;

    private final AutoClickService.StatsListener statsListener =
            (total, rate) -> {
                if (totalClicksLabel != null) {
                    totalClicksLabel.setText("Clics: " + total);
                }

                if (actualSpeedLabel != null) {
                    actualSpeedLabel.setText(
                            "Velocidad actual: " + rate + "/seg"
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

        android.util.DisplayMetrics metrics =
                new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelParams = new WindowManager.LayoutParams(
                panelWidth,
                panelHeight,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = 20;
        panelParams.y = 120;

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(8, 8, 8, 4);
        panel.setBackground(createPanelBackground());

        TextView title = new TextView(this);
        title.setText("Scg-92 AutoClickerBB");
        title.setTextColor(Color.BLACK);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(8, 8, 8, 8);
        title.setBackgroundColor(Color.rgb(240, 240, 240));
        panel.addView(title);

        makeDraggable(title, panelParams, panel);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        totalClicksLabel = new TextView(this);
        totalClicksLabel.setTextColor(Color.rgb(0, 120, 0));
        totalClicksLabel.setTextSize(16);
        totalClicksLabel.setText("Clics: 0");
        content.addView(totalClicksLabel);

        actualSpeedLabel = new TextView(this);
        actualSpeedLabel.setTextColor(Color.DKGRAY);
        actualSpeedLabel.setTextSize(14);
        actualSpeedLabel.setText("Velocidad actual: 0/seg");
        content.addView(actualSpeedLabel);

        AutoClickService existing = AutoClickService.getInstance();
        if (existing != null) {
            existing.setStatsListener(statsListener);
        }

        // Botones compactos de clics.
        LinearLayout clickButtons = new LinearLayout(this);
        clickButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button start = new Button(this);
        start.setText("▶");
        start.setTextSize(22);
        start.setContentDescription("Iniciar clics");
        clickButtons.addView(start, new LinearLayout.LayoutParams(
                0, dp(52), 1f
        ));

        Button stop = new Button(this);
        stop.setText("■");
        stop.setTextSize(22);
        stop.setContentDescription("Detener clics");
        clickButtons.addView(stop, new LinearLayout.LayoutParams(
                0, dp(52), 1f
        ));

        content.addView(clickButtons);

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
            s.setInterval(Math.max(
                    MIN_INTERVAL_MS, 1000L / clicksPerSecond
            ));
            s.startClicks();

            Toast.makeText(
                    this, "Clics iniciados", Toast.LENGTH_SHORT
            ).show();
        });

        stop.setOnClickListener(v -> {
            AutoClickService s = AutoClickService.getInstance();
            if (s != null) {
                s.stopClicks();
            }

            Toast.makeText(
                    this, "Clics detenidos", Toast.LENGTH_SHORT
            ).show();
        });

        // Velocidad ajustada con botones - y +.
        configuredSpeedLabel = new TextView(this);
        configuredSpeedLabel.setTextColor(Color.BLACK);
        configuredSpeedLabel.setTextSize(15);
        configuredSpeedLabel.setGravity(Gravity.CENTER);
        updateConfiguredSpeedLabel();
        content.addView(configuredSpeedLabel);

        LinearLayout speedButtons = new LinearLayout(this);
        speedButtons.setOrientation(LinearLayout.HORIZONTAL);
        speedButtons.setGravity(Gravity.CENTER);

        Button slower = new Button(this);
        slower.setText("−");
        slower.setTextSize(24);
        speedButtons.addView(slower, new LinearLayout.LayoutParams(
                0, dp(48), 1f
        ));

        Button faster = new Button(this);
        faster.setText("+");
        faster.setTextSize(24);
        speedButtons.addView(faster, new LinearLayout.LayoutParams(
                0, dp(48), 1f
        ));

        content.addView(speedButtons);

        slower.setOnClickListener(v -> changeSpeed(-1));
        faster.setOnClickListener(v -> changeSpeed(1));

        TextView markerLabel = new TextView(this);
        markerLabel.setTextColor(Color.BLACK);
        markerLabel.setText("Tamaño del anillo");
        content.addView(markerLabel);

        SeekBar markerSizeSeek = new SeekBar(this);
        markerSizeSeek.setMax(100);
        markerSizeSeek.setProgress(markerSize - 20);

        markerSizeSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b, int progress, boolean fromUser
                    ) {
                        markerSize = progress + 20;

                        if (marker != null && markerParams != null) {
                            markerParams.width = markerSize;
                            markerParams.height = markerSize;

                            try {
                                wm.updateViewLayout(marker, markerParams);
                            } catch (IllegalArgumentException ignored) {
                            }

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
        content.addView(markerSizeSeek);

        // La captura necesita autorización de Android.
        Button startDetection = new Button(this);
        startDetection.setText("👁 Detectar");
        startDetection.setContentDescription("Iniciar detección");
        content.addView(startDetection);

        startDetection.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            intent.setAction("com.autoclicker.android.REQUEST_CAPTURE");
            startActivity(intent);

            Toast.makeText(
                    this,
                    "Abre Iniciar detección en la pantalla principal",
                    Toast.LENGTH_LONG
            ).show();
        });

        Button stopDetection = new Button(this);
        stopDetection.setText("■ Parar detección");
        content.addView(stopDetection);

        stopDetection.setOnClickListener(v -> {
            Intent intent = new Intent(
                    this, ScreenCaptureService.class
            );
            intent.setAction(ScreenCaptureService.ACTION_STOP);

            try {
                startService(intent);
                Toast.makeText(
                        this,
                        "Solicitud de detener detección enviada",
                        Toast.LENGTH_SHORT
                ).show();
            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "No se pudo detener la captura",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        Button close = new Button(this);
        close.setText("✕ Cerrar");
        close.setOnClickListener(v -> stopSelf());
        content.addView(close);

        panel.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        FrameLayout resizeArea = new FrameLayout(this);
        resizeArea.setMinimumHeight(dp(32));

        TextView resizeHandle = new TextView(this);
        resizeHandle.setText("◢");
        resizeHandle.setTextColor(Color.DKGRAY);
        resizeHandle.setTextSize(25);
        resizeHandle.setGravity(Gravity.CENTER);
        resizeHandle.setContentDescription(
                "Arrastrar para cambiar tamaño"
        );

        FrameLayout.LayoutParams handleParams =
                new FrameLayout.LayoutParams(
                        dp(44), dp(32),
                        Gravity.RIGHT | Gravity.BOTTOM
                );

        resizeArea.addView(resizeHandle, handleParams);
        panel.addView(resizeArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)
        ));

        makeResizable(resizeHandle);

        wm.addView(panel, panelParams);
        createMarker(type);
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private void changeSpeed(int change) {
        clicksPerSecond = clamp(
                clicksPerSecond + change,
                MIN_CLICKS_PER_SECOND,
                MAX_CLICKS_PER_SECOND
        );

        updateConfiguredSpeedLabel();

        AutoClickService s = AutoClickService.getInstance();
        if (s != null) {
            s.setInterval(Math.max(
                    MIN_INTERVAL_MS, 1000L / clicksPerSecond
            ));
        }
    }

    private void updateConfiguredSpeedLabel() {
        if (configuredSpeedLabel != null) {
            configuredSpeedLabel.setText(
                    "Velocidad: " + clicksPerSecond + " clics/seg"
            );
        }
    }

    private GradientDrawable createPanelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(2), Color.LTGRAY);
        return bg;
    }

    private void makeResizable(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            private int originalWidth;
            private int originalHeight;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        originalWidth = panelWidth;
                        originalHeight = panelHeight;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - startX);
                        int dy = (int) (event.getRawY() - startY);

                        panelWidth = clamp(
                                originalWidth + dx,
                                dp(MIN_PANEL_WIDTH),
                                screenWidth
                        );

                        panelHeight = clamp(
                                originalHeight + dy,
                                dp(MIN_PANEL_HEIGHT),
                                screenHeight
                        );

                        panelParams.width = panelWidth;
                        panelParams.height = panelHeight;

                        try {
                            wm.updateViewLayout(panel, panelParams);
                        } catch (IllegalArgumentException ignored) {
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;

                    default:
                        return true;
                }
            }
        });
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void createMarker(int type) {
        marker = new TextView(this);
        marker.setText("");
        marker.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(dp(4), Color.YELLOW);
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
        makeDraggable(marker, markerParams, marker);
        updateClickPosition();
    }

    private void updateClickPosition() {
        if (marker == null || markerParams == null) {
            return;
        }

        int x = markerParams.x + markerSize / 2;
        int y = markerParams.y + markerSize / 2;

        AutoClickService s = AutoClickService.getInstance();
        if (s != null) {
            s.setPosition(x, y);
        }
    }

    private void makeDraggable(
            View touchView,
            WindowManager.LayoutParams params,
            View viewToMove
    ) {
        touchView.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            private int originalX;
            private int originalY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = event.getRawX();
                    startY = event.getRawY();
                    originalX = params.x;
                    originalY = params.y;
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = originalX
                            + (int) (event.getRawX() - startX);
                    params.y = originalY
                            + (int) (event.getRawY() - startY);

                    try {
                        wm.updateViewLayout(viewToMove, params);
                    } catch (IllegalArgumentException ignored) {
                    }

                    if (viewToMove == marker) {
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
        configuredSpeedLabel = null;

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
