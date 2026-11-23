package com.autoclicker.android;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import java.net.HttpURLConnection;
import java.net.URL;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.text.InputType;
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
    private static final int DRAG_THRESHOLD_DP = 3;

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
        refreshScreenSize();

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelParams = new WindowManager.LayoutParams(
                panelWidth,
                panelHeight,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(20);
        panelParams.y = dp(120);

        createPanel(type);
        createMarker(type);
    }

    private void refreshScreenSize() {
        if (wm == null) return;

        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
    }

    private void createPanel(int type) {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(4));
        panel.setBackground(createPanelBackground());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.rgb(240, 240, 240));

        TextView title = new TextView(this);
        title.setText("Scg-92 AutoClickerBB");
        title.setTextColor(Color.BLACK);
        title.setTextSize(16);
        title.setPadding(dp(8), dp(8), dp(4), dp(8));
        title.setContentDescription("Arrastrar panel");

        header.addView(title, new LinearLayout.LayoutParams(
                0, -2, 1f
        ));

        Button minimize = new Button(this);
        minimize.setText("−");
        minimize.setTextSize(18);
        minimize.setContentDescription("Minimizar panel");

        header.addView(minimize, new LinearLayout.LayoutParams(
                dp(48), dp(44)
        ));
        minimize.setOnClickListener(v -> minimizePanel());

        panel.addView(header);

        makeDraggable(header, panelParams, panel);

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

        Button adjustSpeed = new Button(this);
        adjustSpeed.setText("⚙ Ajustar velocidad");
        adjustSpeed.setTextSize(15);
        content.addView(adjustSpeed, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
        ));
        adjustSpeed.setOnClickListener(v -> showSpeedOptions());

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
                            SeekBar seekBar, int progress, boolean fromUser
                    ) {
                        markerSize = progress + 20;

                        if (marker != null && markerParams != null) {
                            markerParams.width = dp(markerSize);
                            markerParams.height = dp(markerSize);
                            keepMarkerInsideScreen();

                            try {
                                wm.updateViewLayout(marker, markerParams);
                            } catch (IllegalArgumentException ignored) {
                            }
                            updateClickPosition();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );
        content.addView(markerSizeSeek);

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
                        dp(44), dp(32), Gravity.RIGHT | Gravity.BOTTOM
                );
        resizeArea.addView(resizeHandle, handleParams);
        panel.addView(resizeArea, new LinearLayout.LayoutParams(
                -1, dp(32)
        ));

        makeResizable(resizeHandle);
        wm.addView(panel, panelParams);
    }

    private void startClicks() {
        AutoClickService service = AutoClickService.getInstance();

        if (service == null) {
            Toast.makeText(
                    this,
                    "Activa AutoClicker en Accesibilidad",
                    Toast.LENGTH_LONG
            ).show();

            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        updateClickPosition();
        service.setStatsListener(statsListener);
        service.setInterval(Math.max(
                MIN_INTERVAL_MS, 1000L / clicksPerSecond
        ));
        service.startClicks();

        setMarkerTouchable(false);

        Toast.makeText(
                this, "Clics iniciados", Toast.LENGTH_SHORT
        ).show();
    }

    private void stopClicks() {
        AutoClickService service = AutoClickService.getInstance();
        if (service != null) {
            service.stopClicks();
        }

        setMarkerTouchable(true);

        Toast.makeText(
                this, "Clics detenidos", Toast.LENGTH_SHORT
        ).show();
    }

    private void setMarkerTouchable(boolean touchable) {
        if (marker == null || markerParams == null || wm == null) return;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        if (!touchable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        markerParams.flags = flags;

        try {
            wm.updateViewLayout(marker, markerParams);
        } catch (IllegalArgumentException ignored) {
        }
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

        TextView dragHandle = new TextView(this);
        dragHandle.setText("☰");
        dragHandle.setTextSize(20);
        dragHandle.setTextColor(Color.WHITE);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setContentDescription("Arrastrar panel minimizado");
        miniBubble.addView(dragHandle, new LinearLayout.LayoutParams(
                dp(38), dp(48)
        ));

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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        miniParams.gravity = Gravity.TOP | Gravity.START;
        miniParams.x = Math.max(0, screenWidth - dp(230));
        miniParams.y = dp(120);

        wm.addView(miniBubble, miniParams);
        makeMiniDraggable(dragHandle);
    }

    private void expandPanel() {
        if (!minimized || panel == null) return;

        try {
            if (miniBubble != null) {
                wm.removeView(miniBubble);
            }
        } catch (IllegalArgumentException ignored) {
        }

        miniBubble = null;
        refreshScreenSize();
        keepPanelInsideScreen();
        wm.addView(panel, panelParams);
        minimized = false;
    }

    private void makeMiniDraggable(View dragHandle) {
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            private int originalX;
            private int originalY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        refreshScreenSize();
                        startX = event.getRawX();
                        startY = event.getRawY();
                        originalX = miniParams.x;
                        originalY = miniParams.y;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        refreshScreenSize();
                        int dx = Math.round(event.getRawX() - startX);
                        int dy = Math.round(event.getRawY() - startY);

                        miniParams.x = clamp(
                                originalX + dx,
                                0,
                                Math.max(0, screenWidth - miniBubble.getWidth())
                        );
                        miniParams.y = clamp(
                                originalY + dy,
                                0,
                                Math.max(0, screenHeight - miniBubble.getHeight())
                        );

                        try {
                            wm.updateViewLayout(miniBubble, miniParams);
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

    private void showSpeedOptions() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ajustar velocidad")
                .setItems(
                        new String[]{
                                "Introducir número",
                                "Barra deslizante"
                        },
                        (ignoredDialog, which) -> {
                            if (which == 0) {
                                showNumericSpeedDialog();
                            } else {
                                showSliderSpeedDialog();
                            }
                        }
                )
                .setNegativeButton("Cancelar", null)
                .create();
        showOverlayDialog(dialog);
    }

    private void showNumericSpeedDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(String.valueOf(clicksPerSecond));
        input.setSelection(input.getText().length());
        input.setHint("Entre 1 y " + MAX_CLICKS_PER_SECOND);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Velocidad en clics por segundo")
                .setMessage("Introduce un valor entre 1 y "
                        + MAX_CLICKS_PER_SECOND + ".")
                .setView(input)
                .setPositiveButton("Aplicar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String value = input.getText().toString().trim();
                            try {
                                int requested = Integer.parseInt(value);
                                if (requested < 1
                                        || requested > MAX_CLICKS_PER_SECOND) {
                                    input.setError("Usa un valor entre 1 y "
                                            + MAX_CLICKS_PER_SECOND);
                                    return;
                                }
                                setSpeed(requested);
                                dialog.dismiss();
                            } catch (NumberFormatException e) {
                                input.setError("Introduce un número válido");
                            }
                        })
        );
        showOverlayDialog(dialog);
    }

    private void showSliderSpeedDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(4));

        TextView valueLabel = new TextView(this);
        valueLabel.setTextSize(18);
        valueLabel.setTextColor(Color.BLACK);
        valueLabel.setGravity(Gravity.CENTER);
        valueLabel.setText(clicksPerSecond + " clics/seg");
        layout.addView(valueLabel);

        SeekBar speedSeek = new SeekBar(this);
        speedSeek.setMax(MAX_CLICKS_PER_SECOND - 1);
        speedSeek.setProgress(clicksPerSecond - 1);
        layout.addView(speedSeek);

        speedSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar, int progress, boolean fromUser
                    ) {
                        valueLabel.setText((progress + 1) + " clics/seg");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Desliza para ajustar")
                .setView(layout)
                .setPositiveButton("Aplicar", (ignoredDialog, which) ->
                        setSpeed(speedSeek.getProgress() + 1))
                .setNegativeButton("Cancelar", null)
                .create();
        showOverlayDialog(dialog);
    }

    private void showOverlayDialog(AlertDialog dialog) {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(type);
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            );
        }
        dialog.show();
    }

    private void setSpeed(int speed) {
        clicksPerSecond = clamp(speed, 1, MAX_CLICKS_PER_SECOND);
        updateConfiguredSpeedLabel();

        AutoClickService service = AutoClickService.getInstance();
        if (service != null) {
            service.setInterval(Math.max(
                    MIN_INTERVAL_MS, 1000L / clicksPerSecond
            ));
        }
    }

    private void changeSpeed(int change) {
        clicksPerSecond = clamp(
                clicksPerSecond + change,
                1,
                MAX_CLICKS_PER_SECOND
        );
        updateConfiguredSpeedLabel();

        AutoClickService service = AutoClickService.getInstance();
        if (service != null) {
            service.setInterval(Math.max(
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
        GradientDrawable fallback = new GradientDrawable();
        fallback.setColor(Color.rgb(24, 43, 54));
        fallback.setCornerRadius(dp(8));
        fallback.setStroke(dp(2), Color.rgb(40, 130, 160));
        loadRingBackgroundImage();
        return fallback;
    }

    private void loadRingBackgroundImage() {
        new Thread(() -> {
            Bitmap downloaded;
            HttpURLConnection connection = null;
            try {
                URL imageUrl = new URL(
                        "https://static.boombang.tv/resources/uploads/"
                                + "ring-playero-ropero-vip-20260923-v2.png"
                );
                connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setDoInput(true);
                connection.connect();

                try (java.io.InputStream stream = connection.getInputStream()) {
                    downloaded = BitmapFactory.decodeStream(stream);
                }
            } catch (Exception ignored) {
                return;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            if (downloaded == null) return;

            Bitmap image = downloaded;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (panel == null) return;

                int targetWidth = Math.max(1, panelWidth);
                int targetHeight = Math.max(1, panelHeight);
                float targetRatio = targetWidth / (float) targetHeight;
                float imageRatio = image.getWidth() / (float) image.getHeight();

                Bitmap cropped = image;
                if (imageRatio > targetRatio) {
                    int cropWidth = Math.max(
                            1, Math.min(image.getWidth(),
                                    Math.round(image.getHeight() * targetRatio))
                    );
                    int left = (image.getWidth() - cropWidth) / 2;
                    cropped = Bitmap.createBitmap(
                            image, left, 0, cropWidth, image.getHeight()
                    );
                } else if (imageRatio < targetRatio) {
                    int cropHeight = Math.max(
                            1, Math.min(image.getHeight(),
                                    Math.round(image.getWidth() / targetRatio))
                    );
                    int top = (image.getHeight() - cropHeight) / 2;
                    cropped = Bitmap.createBitmap(
                            image, 0, top, image.getWidth(), cropHeight
                    );
                }

                BitmapDrawable photo = new BitmapDrawable(getResources(), cropped);
                photo.setGravity(Gravity.FILL);
                ColorDrawable softOverlay = new ColorDrawable(
                        Color.argb(78, 255, 255, 255)
                );
                LayerDrawable layers = new LayerDrawable(
                        new Drawable[]{photo, softOverlay}
                );
                panel.setBackground(layers);
            });
        }).start();
    }

    private void makeResizable(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            private int originalWidth;
            private int originalHeight;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        refreshScreenSize();
                        startX = event.getRawX();
                        startY = event.getRawY();
                        originalWidth = panelWidth;
                        originalHeight = panelHeight;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        refreshScreenSize();
                        int dx = Math.round(event.getRawX() - startX);
                        int dy = Math.round(event.getRawY() - startY);

                        panelWidth = clamp(
                                originalWidth + dx,
                                dp(MIN_PANEL_WIDTH),
                                Math.max(dp(MIN_PANEL_WIDTH), screenWidth)
                        );
                        panelHeight = clamp(
                                originalHeight + dy,
                                dp(MIN_PANEL_HEIGHT),
                                Math.max(dp(MIN_PANEL_HEIGHT), screenHeight)
                        );

                        panelParams.width = panelWidth;
                        panelParams.height = panelHeight;
                        keepPanelInsideScreen();

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

    private void createMarker(int type) {
        marker = new TextView(this);
        marker.setGravity(Gravity.CENTER);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setShape(GradientDrawable.OVAL);
        background.setStroke(dp(4), Color.YELLOW);
        marker.setBackground(background);

        markerParams = new WindowManager.LayoutParams(
                dp(markerSize),
                dp(markerSize),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        markerParams.gravity = Gravity.TOP | Gravity.START;
        markerParams.x = dp(300);
        markerParams.y = dp(500);

        refreshScreenSize();
        keepMarkerInsideScreen();

        wm.addView(marker, markerParams);
        makeDraggable(marker, markerParams, marker);
        updateClickPosition();
    }

    private void keepMarkerInsideScreen() {
        refreshScreenSize();
        if (markerParams == null) return;

        markerParams.x = clamp(
                markerParams.x,
                0,
                Math.max(0, screenWidth - markerParams.width)
        );
        markerParams.y = clamp(
                markerParams.y,
                0,
                Math.max(0, screenHeight - markerParams.height)
        );
    }

    private void keepPanelInsideScreen() {
        refreshScreenSize();
        if (panelParams == null) return;

        panelParams.x = clamp(
                panelParams.x,
                0,
                Math.max(0, screenWidth - panelParams.width)
        );
        panelParams.y = clamp(
                panelParams.y,
                0,
                Math.max(0, screenHeight - panelParams.height)
        );
    }

    private void updateClickPosition() {
        if (marker == null || markerParams == null) return;

        int x = markerParams.x + markerParams.width / 2;
        int y = markerParams.y + markerParams.height / 2;

        AutoClickService service = AutoClickService.getInstance();
        if (service != null) {
            service.setPosition(x, y);
        }
    }

    private void makeDraggable(
            View touchView,
            WindowManager.LayoutParams params,
            View view
    ) {
        touchView.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            private int originalX;
            private int originalY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        refreshScreenSize();
                        startX = event.getRawX();
                        startY = event.getRawY();
                        originalX = params.x;
                        originalY = params.y;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        refreshScreenSize();
                        int dx = Math.round(event.getRawX() - startX);
                        int dy = Math.round(event.getRawY() - startY);

                        params.x = originalX + dx;
                        params.y = originalY + dy;

                        int maxX;
                        int maxY;

                        if (view == marker) {
                            maxX = Math.max(0, screenWidth - params.width);
                            maxY = Math.max(0, screenHeight - params.height);
                        } else {
                            maxX = Math.max(0, screenWidth - panelWidth);
                            maxY = Math.max(0, screenHeight - panelHeight);
                        }

                        params.x = clamp(params.x, 0, maxX);
                        params.y = clamp(params.y, 0, maxY);

                        try {
                            wm.updateViewLayout(view, params);
                        } catch (IllegalArgumentException ignored) {
                            return true;
                        }

                        if (view == marker) {
                            updateClickPosition();
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

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private int clamp(int value, int min, int max) {
        if (max < min) max = min;
        return Math.max(min, Math.min(value, max));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        AutoClickService service = AutoClickService.getInstance();
        if (service != null) {
            service.setStatsListener(null);
        }

        if (wm != null) {
            removeViewSafely(panel);
            removeViewSafely(marker);
            removeViewSafely(miniBubble);
        }

        panel = null;
        marker = null;
        miniBubble = null;

        super.onDestroy();
    }

    private void removeViewSafely(View view) {
        if (view == null || wm == null) return;

        try {
            wm.removeView(view);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
