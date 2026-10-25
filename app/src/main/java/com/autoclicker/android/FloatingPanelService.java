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
    private LinearLayout miniBubble;
    private TextView marker;
    private TextView totalClicksLabel;
    private TextView actualSpeedLabel;
    private TextView configuredSpeedLabel;

    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams markerParams;
    private WindowManager.LayoutParams miniParams;

    private int markerSize = 48;
    private int clicksPerSecond = 10;
    private int panelWidth = 300;
    private int panelHeight = 440;
    private int screenWidth;
    private int screenHeight;
    private boolean minimized = false;

    private static final int MIN_PANEL_WIDTH = 200;
    private static final int MIN_PANEL_HEIGHT = 180;
    private static final int MAX_CLICKS_PER_SECOND = 1500;
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
            Toast.makeText(this,
                    "Permite mostrar sobre otras aplicaciones",
                    Toast.LENGTH_LONG).show();
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
                panelWidth, panelHeight, type,
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

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.rgb(240, 240, 240));

        TextView title = new TextView(this);
        title.setText("Scg-92 AutoClickerBB");
        title.setTextColor(Color.BLACK);
        title.setTextSize(16);
        title.setPadding(8, 8, 4, 8);
        header.addView(title, new LinearLayout.LayoutParams(
                0, -2, 1f
        ));
        makeDraggable(title, panelParams, panel);

        Button minimize = new Button(this);
        minimize.setText("−");
        minimize.setTextSize(18);
        minimize.setContentDescription("Minimizar panel");
        header.addView(minimize, new LinearLayout.LayoutParams(
                dp(48), dp(44)
        ));
        minimize.setOnClickListener(v -> minimizePanel());

        panel.addView(header);

        ScrollView scroll = new ScrollView(this);
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

        LinearLayout clickButtons = new LinearLayout(this);
        clickButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button start = new Button(this);
        start.setText("▶");
        start.setTextSize(22);
        start.setContentDescription("Iniciar clics");
        clickButtons.addView(start, new LinearLayout.LayoutParams(
                0, dp(48), 1f
        ));

        Button stop = new Button(this);
        stop.setText("■");
        stop.setTextSize(22);
        stop.setContentDescription("Detener clics");
        clickButtons.addView(stop, new LinearLayout.LayoutParams(
                0, dp(48), 1f
        ));
        content.addView(clickButtons);

        start.setOnClickListener(v -> startClicks());
        stop.setOnClickListener(v -> stopClicks());

        configuredSpeedLabel = new TextView(this);
        configuredSpeedLabel.setTextColor(Color.BLACK);
        configuredSpeedLabel.setTextSize(15);
        configuredSpeedLabel.setGravity(Gravity.CENTER);
        updateConfiguredSpeedLabel();
        content.addView(configuredSpeedLabel);

        LinearLayout speedButtons = new LinearLayout(this);
        speedButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button slower = new Button(this);
        slower.setText("−");
        slower.setTextSize(24);
        speedButtons.addView(slower, new LinearLayout.LayoutParams(
                0, dp(46), 1f
        ));

        Button faster = new Button(this);
        faster.setText("+");
        faster.setTextSize(24);
        speedButtons.addView(faster, new LinearLayout.LayoutParams(
                0, dp(46), 1f
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
                            SeekBar b, int progress, boolean fromUser) {
                        markerSize = progress + 20;
                        if (marker != null && markerParams != null) {
                            markerParams.width = dp(markerSize);
                            markerParams.height = dp(markerSize);
                            try {
                                wm.updateViewLayout(marker, markerParams);
                            } catch (IllegalArgumentException ignored) {
                            }
                            updateClickPosition();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar b) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar b) {}
                });
        content.addView(markerSizeSeek);

        Button startDetection = new Button(this);
        startDetection.setText("👁 Detectar");
        content.addView(startDetection);
        startDetection.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(MainActivity.ACTION_START_DETECTION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this,
                        "No se pudo abrir la autorización de captura",
                        Toast.LENGTH_LONG).show();
            }
        });

        Button stopDetection = new Button(this);
        stopDetection.setText("■ Parar detección");
        content.addView(stopDetection);
        stopDetection.setOnClickListener(v -> {
            Intent intent = new Intent(
                    this, ScreenCaptureService.class);
            intent.setAction(ScreenCaptureService.ACTION_STOP);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                Toast.makeText(this, "Deteniendo captura",
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo detener la captura",
                        Toast.LENGTH_LONG).show();
            }
        });

        Button close = new Button(this);
        close.setText("✕ Cerrar panel");
        close.setOnClickListener(v -> stopSelf());
        content.addView(close);

        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        FrameLayout resizeArea = new FrameLayout(this);
        TextView resizeHandle = new TextView(this);
        resizeHandle.setText("◢");
        resizeHandle.setTextColor(Color.DKGRAY);
        resizeHandle.setTextSize(25);
        resizeHandle.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams handleParams =
                new FrameLayout.LayoutParams(
                        dp(44), dp(32), Gravity.RIGHT | Gravity.BOTTOM);
        resizeArea.addView(resizeHandle, handleParams);
        panel.addView(resizeArea, new LinearLayout.LayoutParams(
                -1, dp(32)
        ));
        makeResizable(resizeHandle);

        wm.addView(panel, panelParams);
        createMarker(type);
    }

    private void startClicks() {
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
        s.setStatsListener(statsListener);
        s.setInterval(Math.max(
                MIN_INTERVAL_MS, 1000L / clicksPerSecond));
        s.startClicks();
        Toast.makeText(this, "Clics iniciados",
                Toast.LENGTH_SHORT).show();
    }

    private void stopClicks() {
        AutoClickService s = AutoClickService.getInstance();
        if (s != null) s.stopClicks();
        Toast.makeText(this, "Clics detenidos",
                Toast.LENGTH_SHORT).show();
    }

    private void minimizePanel() {
        if (minimized || panel == null) return;

        try {
            wm.removeView(panel);
        } catch (IllegalArgumentException ignored) {
        }

        createMiniBubble();
        minimized = true;
    }

    private void createMiniBubble() {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        miniBubble = new LinearLayout(this);
        miniBubble.setOrientation(LinearLayout.HORIZONTAL);
        miniBubble.setGravity(Gravity.CENTER);
        miniBubble.setPadding(dp(4), dp(2), dp(4), dp(2));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(225, 25, 35, 55));
        background.setCornerRadius(dp(30));
        background.setStroke(dp(2), Color.rgb(40, 130, 255));
        miniBubble.setBackground(background);

        Button play = new Button(this);
        play.setText("▶");
        play.setTextSize(16);
        play.setContentDescription("Iniciar clics");
        miniBubble.addView(play, new LinearLayout.LayoutParams(
                dp(48), dp(48)
        ));
        play.setOnClickListener(v -> startClicks());

        Button stop = new Button(this);
        stop.setText("■");
        stop.setTextSize(16);
        stop.setContentDescription("Detener clics");
        miniBubble.addView(stop, new LinearLayout.LayoutParams(
                dp(48), dp(48)
        ));
        stop.setOnClickListener(v -> stopClicks());

        Button expand = new Button(this);
        expand.setText("↗");
        expand.setTextSize(18);
        expand.setContentDescription("Expandir panel");
        miniBubble.addView(expand, new LinearLayout.LayoutParams(
                dp(44), dp(48)
        ));
        expand.setOnClickListener(v -> expandPanel());

        miniParams = new WindowManager.LayoutParams(
                -2, -2, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        miniParams.gravity = Gravity.TOP | Gravity.START;
        miniParams.x = Math.max(0, screenWidth - dp(160));
        miniParams.y = dp(120);

        wm.addView(miniBubble, miniParams);
        makeMiniDraggable();
    }

    private void expandPanel() {
        if (!minimized || panel == null) return;

        try {
            if (miniBubble != null) wm.removeView(miniBubble);
        } catch (IllegalArgumentException ignored) {
        }

        miniBubble = null;
        wm.addView(panel, panelParams);
        minimized = false;
    }

    private void makeMiniDraggable() {
        miniBubble.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private int originalX, originalY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        originalX = miniParams.x;
                        originalY = miniParams.y;
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - startX);
                        int dy = (int) (event.getRawY() - startY);
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) {
                            moved = true;
                        }

                        miniParams.x = clamp(
                                originalX + dx, 0,
                                Math.max(0, screenWidth - miniBubble.getWidth()));
                        miniParams.y = clamp(
                                originalY + dy, 0,
                                Math.max(0, screenHeight - miniBubble.getHeight()));

                        try {
                            wm.updateViewLayout(miniBubble, miniParams);
                        } catch (IllegalArgumentException ignored) {
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!moved) expandPanel();
                        return true;

                    default:
                        return true;
                }
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private void changeSpeed(int change) {
        clicksPerSecond = clamp(
                clicksPerSecond + change, 1, MAX_CLICKS_PER_SECOND);
        updateConfiguredSpeedLabel();

        AutoClickService s = AutoClickService.getInstance();
        if (s != null) {
            s.setInterval(Math.max(
                    MIN_INTERVAL_MS, 1000L / clicksPerSecond));
        }
    }

    private void updateConfiguredSpeedLabel() {
        if (configuredSpeedLabel != null) {
            configuredSpeedLabel.setText(
                    "Velocidad: " + clicksPerSecond + " clics/seg");
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
            private float startX, startY;
            private int originalWidth, originalHeight;

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
                        panelWidth = clamp(originalWidth + dx,
                                dp(MIN_PANEL_WIDTH), screenWidth);
                        panelHeight = clamp(originalHeight + dy,
                                dp(MIN_PANEL_HEIGHT), screenHeight);
                        panelParams.width = panelWidth;
                        panelParams.height = panelHeight;
                        try {
                            wm.updateViewLayout(panel, panelParams);
                        } catch (IllegalArgumentException ignored) {
                        }
                        return true;

                    default:
                        return true;
                }
            }
        });
    }

    private void createMarker(int type) {
        marker = new TextView(this);
        marker.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(dp(4), Color.YELLOW);
        marker.setBackground(bg);

        markerParams = new WindowManager.LayoutParams(
                dp(markerSize), dp(markerSize), type,
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
        if (marker == null || markerParams == null) return;

        int x = markerParams.x + dp(markerSize) / 2;
        int y = markerParams.y + dp(markerSize) / 2;

        AutoClickService s = AutoClickService.getInstance();
        if (s != null) s.setPosition(x, y);
    }

    private void makeDraggable(
            View touchView,
            WindowManager.LayoutParams params,
            View view) {

        touchView.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private int originalX, originalY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        originalX = params.x;
                        originalY = params.y;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = originalX
                                + (int) (event.getRawX() - startX);
                        params.y = originalY
                                + (int) (event.getRawY() - startY);

                        params.x = clamp(params.x, 0,
                                Math.max(0, screenWidth - view.getWidth()));
                        params.y = clamp(params.y, 0,
                                Math.max(0, screenHeight - view.getHeight()));

                        try {
                            wm.updateViewLayout(view, params);
                        } catch (IllegalArgumentException ignored) {
                        }

                        if (view == marker) updateClickPosition();
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        AutoClickService s = AutoClickService.getInstance();
        if (s != null) s.setStatsListener(null);

        try {
            if (panel != null) wm.removeView(panel);
        } catch (Exception ignored) {
        }

        try {
            if (marker != null) wm.removeView(marker);
        } catch (Exception ignored) {
        }

        try {
            if (miniBubble != null) wm.removeView(miniBubble);
        } catch (Exception ignored) {
        }
    }
}
