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
    private TextView coordinatesLabel;
    private TextView totalClicksLabel;
    private TextView actualSpeedLabel;

    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams markerParams;

    private int markerSize = 48;
    private int clicksPerSecond = 10;

    private int panelWidth = 300;
    private int panelHeight = 400;

    private int screenWidth;
    private int screenHeight;

    private static final int MIN_PANEL_WIDTH = 200;
    private static final int MIN_PANEL_HEIGHT = 180;
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
                            "Velocidad actual: " + rate + " clics/seg"
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

        // Encabezado: arrástralo para mover el panel.
        TextView title = new TextView(this);
        title.setText("Scg-92 autoclikerbb");
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

        coordinatesLabel = new TextView(this);
        coordinatesLabel.setTextColor(Color.BLUE);
        coordinatesLabel.setTextSize(16);
        coordinatesLabel.setText("X: -- | Y: --");
        content.addView(coordinatesLabel);

        totalClicksLabel = new TextView(this);
        totalClicksLabel.setTextColor(Color.rgb(0, 120, 0));
        totalClicksLabel.setTextSize(18);
        totalClicksLabel.setText("Clics completados: 0");
        content.addView(totalClicksLabel);

        actualSpeedLabel = new TextView(this);
        actualSpeedLabel.setTextColor(Color.BLACK);
        actualSpeedLabel.setTextSize(16);
        actualSpeedLabel.setText("Velocidad actual: 0 clics/seg");
        content.addView(actualSpeedLabel);

        AutoClickService existing = AutoClickService.getInstance();
        if (existing != null) {
            existing.setStatsListener(statsListener);
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
                    Math.max(MIN_INTERVAL_MS, 1000L / clicksPerSecond)
            );
            s.startClicks();

            Toast.makeText(
                    this,
                    "Clics iniciados desde cero",
                    Toast.LENGTH_SHORT
            ).show();
        });
        content.addView(start);

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
        content.addView(stop);

        TextView speedLabel = new TextView(this);
        speedLabel.setTextColor(Color.BLACK);
        speedLabel.setText("Velocidad configurada: 10 clics/seg");
        content.addView(speedLabel);

        SeekBar speed = new SeekBar(this);
        speed.setMax(MAX_CLICKS_PER_SECOND - 1);
        speed.setProgress(clicksPerSecond - 1);

        speed.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b, int progress, boolean fromUser
                    ) {
                        clicksPerSecond = progress + 1;
                        speedLabel.setText(
                                "Velocidad configurada: "
                                        + clicksPerSecond + " clics/seg"
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
        content.addView(speed);

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

        Button close = new Button(this);
        close.setText("CERRAR PANEL");
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

        // Esquina inferior derecha para cambiar el tamaño,
        // como el tirador de un marco de recorte.
        FrameLayout resizeArea = new FrameLayout(this);
        resizeArea.setMinimumHeight(32);

        TextView resizeHandle = new TextView(this);
        resizeHandle.setText("◢");
        resizeHandle.setTextColor(Color.DKGRAY);
        resizeHandle.setTextSize(25);
        resizeHandle.setGravity(Gravity.CENTER);
        resizeHandle.setContentDescription("Arrastrar para cambiar tamaño");

        FrameLayout.LayoutParams handleParams =
                new FrameLayout.LayoutParams(
                        44,
                        32,
                        Gravity.RIGHT | Gravity.BOTTOM
                );

        resizeArea.addView(resizeHandle, handleParams);
        panel.addView(
                resizeArea,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        32
                )
        );

        makeResizable(resizeHandle);

        wm.addView(panel, panelParams);
        createMarker(type);
    }

    private GradientDrawable createPanelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(8);
        bg.setStroke(2, Color.LTGRAY);
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
                                MIN_PANEL_WIDTH,
                                screenWidth
                        );

                        panelHeight = clamp(
                                originalHeight + dy,
                                MIN_PANEL_HEIGHT,
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
        makeDraggable(marker, markerParams, marker);
        updateClickPosition();
    }

    private void updateClickPosition() {
        if (marker == null || markerParams == null) {
            return;
        }

        int x = markerParams.x + markerSize / 2;
        int y = markerParams.y + markerSize / 2;

        if (coordinatesLabel != null) {
            coordinatesLabel.setText("X: " + x + " | Y: " + y);
        }

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
        coordinatesLabel = null;
        totalClicksLabel = null;
        actualSpeedLabel = null;

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
