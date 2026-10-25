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
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingPanelService extends Service {

    private WindowManager wm;
    private LinearLayout panel;
    private LinearLayout panelContent;
    private TextView marker;
    private TextView coordinatesLabel;
    private TextView totalClicksLabel;
    private TextView actualSpeedLabel;
    private TextView widthLabel;
    private TextView heightLabel;

    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams markerParams;

    private int markerSize = 48;
    private int clicksPerSecond = 10;

    // Tamaño inicial del panel en píxeles de pantalla.
    private int panelWidth = 300;
    private int panelHeight = 400;

    private static final int MIN_PANEL_WIDTH = 220;
    private static final int MAX_PANEL_WIDTH = 900;
    private static final int MIN_PANEL_HEIGHT = 180;
    private static final int MAX_PANEL_HEIGHT = 1200;

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
        panel.setPadding(8, 8, 8, 8);
        panel.setBackground(createPanelBackground());

        // Título/asa para mover el panel.
        TextView title = new TextView(this);
        title.setText("Scg-92 autoclikerbb  ⋮");
        title.setTextColor(Color.BLACK);
        title.setTextSize(18);
        title.setPadding(4, 4, 4, 8);
        panel.addView(title);
        makeDraggable(title, panelParams, panel);

        // Permite desplazar el contenido cuando el panel está pequeño.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);

        panelContent = new LinearLayout(this);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panelContent);

        coordinatesLabel = new TextView(this);
        coordinatesLabel.setTextColor(Color.BLUE);
        coordinatesLabel.setTextSize(16);
        coordinatesLabel.setText("X: -- | Y: --");
        panelContent.addView(coordinatesLabel);

        totalClicksLabel = new TextView(this);
        totalClicksLabel.setTextColor(Color.rgb(0, 120, 0));
        totalClicksLabel.setTextSize(18);
        totalClicksLabel.setText("Clics completados: 0");
        panelContent.addView(totalClicksLabel);

        actualSpeedLabel = new TextView(this);
        actualSpeedLabel.setTextColor(Color.BLACK);
        actualSpeedLabel.setTextSize(16);
        actualSpeedLabel.setText("Velocidad actual: 0 clics/seg");
        panelContent.addView(actualSpeedLabel);

        AutoClickService existingService = AutoClickService.getInstance();
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
                    Math.max(MIN_INTERVAL_MS, 1000L / clicksPerSecond)
            );
            s.startClicks();

            Toast.makeText(
                    this,
                    "Clics iniciados desde cero",
                    Toast.LENGTH_SHORT
            ).show();
        });
        panelContent.addView(start);

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
        panelContent.addView(stop);

        TextView speedLabel = new TextView(this);
        speedLabel.setTextColor(Color.BLACK);
        speedLabel.setText("Velocidad configurada: 10 clics/seg");
        panelContent.addView(speedLabel);

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

                        AutoClickService s = AutoClickService.getInstance();
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
        panelContent.addView(speed);

        TextView sizeLabel = new TextView(this);
        sizeLabel.setTextColor(Color.BLACK);
        sizeLabel.setText("Tamaño del anillo");
        panelContent.addView(sizeLabel);

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
        panelContent.addView(markerSizeSeek);

        // Control de ancho del panel.
        widthLabel = new TextView(this);
        widthLabel.setTextColor(Color.BLACK);
        widthLabel.setText("Ancho del panel: " + panelWidth);
        panelContent.addView(widthLabel);

        SeekBar widthSeek = new SeekBar(this);
        widthSeek.setMax(MAX_PANEL_WIDTH - MIN_PANEL_WIDTH);
        widthSeek.setProgress(panelWidth - MIN_PANEL_WIDTH);
        widthSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b, int progress, boolean fromUser
                    ) {
                        panelWidth = MIN_PANEL_WIDTH + progress;
                        widthLabel.setText("Ancho del panel: " + panelWidth);
                        updatePanelSize();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar b) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar b) {
                    }
                });
        panelContent.addView(widthSeek);

        // Control de alto del panel.
        heightLabel = new TextView(this);
        heightLabel.setTextColor(Color.BLACK);
        heightLabel.setText("Alto del panel: " + panelHeight);
        panelContent.addView(heightLabel);

        SeekBar heightSeek = new SeekBar(this);
        heightSeek.setMax(MAX_PANEL_HEIGHT - MIN_PANEL_HEIGHT);
        heightSeek.setProgress(panelHeight - MIN_PANEL_HEIGHT);
        heightSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b, int progress, boolean fromUser
                    ) {
                        panelHeight = MIN_PANEL_HEIGHT + progress;
                        heightLabel.setText("Alto del panel: " + panelHeight);
                        updatePanelSize();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar b) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar b) {
                    }
                });
        panelContent.addView(heightSeek);

        Button close = new Button(this);
        close.setText("CERRAR PANEL");
        close.setOnClickListener(v -> stopSelf());
        panelContent.addView(close);

        panel.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        wm.addView(panel, panelParams);
        createMarker(type);
    }

    private GradientDrawable createPanelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(12);
        bg.setStroke(2, Color.LTGRAY);
        return bg;
    }

    private void updatePanelSize() {
        if (panel == null || panelParams == null || wm == null) {
            return;
        }

        panelParams.width = panelWidth;
        panelParams.height = panelHeight;

        try {
            wm.updateViewLayout(panel, panelParams);
        } catch (IllegalArgumentException ignored) {
        }
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
        coordinatesLabel = null;
        widthLabel = null;
        heightLabel = null;

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
