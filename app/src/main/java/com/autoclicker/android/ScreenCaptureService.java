package com.autoclicker.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureService extends Service {

    public static final String ACTION_START =
            "com.autoclicker.android.SCREEN_CAPTURE_START";
    public static final String ACTION_STOP =
            "com.autoclicker.android.SCREEN_CAPTURE_STOP";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String TAG = "ScreenCaptureService";
    private static final int NOTIFICATION_ID = 902;
    private static final String CHANNEL_ID = "screen_capture";

    // Reducimos la imagen para analizarla sin cargar demasiado el teléfono.
    private static final int ANALYSIS_WIDTH = 180;
    private static final int ANALYSIS_HEIGHT = 320;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread imageThread;
    private Handler imageHandler;

    private long lastAnalysisTime = 0L;
    private boolean processingImage = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(
                EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);

        Intent resultData = getResultIntent(intent);

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Falta el permiso de captura de pantalla");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }

            startCapture(resultCode, resultData);

        } catch (Exception e) {
            Log.e(TAG, "No se pudo iniciar la captura", e);
            stopCapture();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Intent getResultIntent(Intent source) {
        if (Build.VERSION.SDK_INT >= 33) {
            return source.getParcelableExtra(
                    EXTRA_RESULT_DATA, Intent.class);
        }
        return source.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private void startCapture(int resultCode, Intent resultData) {
        releaseCaptureResources();

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);

        if (manager == null) {
            throw new IllegalStateException(
                    "MediaProjectionManager no disponible");
        }

        projection = manager.getMediaProjection(resultCode, resultData);

        if (projection == null) {
            throw new IllegalStateException(
                    "No se pudo obtener MediaProjection");
        }

        android.util.DisplayMetrics metrics =
                new android.util.DisplayMetrics();

        android.view.WindowManager windowManager =
                (android.view.WindowManager) getSystemService(
                        Context.WINDOW_SERVICE);

        if (windowManager == null) {
            throw new IllegalStateException(
                    "WindowManager no disponible");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Rect bounds =
                    windowManager.getCurrentWindowMetrics().getBounds();

            metrics.widthPixels = bounds.width();
            metrics.heightPixels = bounds.height();
            metrics.densityDpi =
                    getResources().getDisplayMetrics().densityDpi;
        } else {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        if (width <= 0 || height <= 0 || density <= 0) {
            throw new IllegalStateException(
                    "Dimensiones de pantalla inválidas");
        }

        imageReader = ImageReader.newInstance(
                width, height, PixelFormat.RGBA_8888, 2);

        imageThread = new HandlerThread("ScreenCaptureThread");
        imageThread.start();
        imageHandler = new Handler(imageThread.getLooper());

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;

            if (processingImage) {
                image = reader.acquireLatestImage();
                if (image != null) image.close();
                return;
            }

            try {
                image = reader.acquireLatestImage();

                if (image != null) {
                    long now = SystemClock.elapsedRealtime();

                    // Analizamos como máximo una vez cada 700 ms.
                    if (now - lastAnalysisTime >= 700L) {
                        lastAnalysisTime = now;
                        processingImage = true;

                        try {
                            List<Detection> detections =
                                    analyzeImage(image);

                            Log.i(TAG,
                                    "Análisis terminado. Posibles zonas: "
                                            + detections.size());

                            for (Detection d : detections) {
                                Log.i(TAG,
                                        "Zona candidata: centro X="
                                                + d.centerX
                                                + ", Y=" + d.centerY
                                                + ", ancho=" + d.width
                                                + ", alto=" + d.height);
                            }
                        } catch (Exception e) {
                            Log.e(TAG,
                                    "Error analizando imagen", e);
                        } finally {
                            processingImage = false;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error recibiendo imagen", e);
                processingImage = false;
            } finally {
                if (image != null) image.close();
            }
        }, imageHandler);

        projection.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.i(TAG, "Permiso de captura finalizado");
                        releaseCaptureResources();
                        stopForeground(true);
                        stopSelf();
                    }
                },
                new Handler(getMainLooper()));

        virtualDisplay = projection.createVirtualDisplay(
                "AutoClickerScreen",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                imageHandler);

        if (virtualDisplay == null) {
            throw new IllegalStateException(
                    "No se pudo crear la pantalla virtual");
        }

        Log.i(TAG, "Captura iniciada: " + width + " x " + height);
    }

    private List<Detection> analyzeImage(Image image) {
        List<Detection> results = new ArrayList<>();

        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return results;

        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        Bitmap small = Bitmap.createBitmap(
                ANALYSIS_WIDTH, ANALYSIS_HEIGHT,
                Bitmap.Config.ARGB_8888);

        int[] pixels = new int[ANALYSIS_WIDTH * ANALYSIS_HEIGHT];

        for (int y = 0; y < ANALYSIS_HEIGHT; y++) {
            int sourceY = y * height / ANALYSIS_HEIGHT;

            for (int x = 0; x < ANALYSIS_WIDTH; x++) {
                int sourceX = x * width / ANALYSIS_WIDTH;

                int offset = sourceY * rowStride
                        + sourceX * pixelStride;

                if (offset + 3 >= buffer.limit()) {
                    pixels[y * ANALYSIS_WIDTH + x] = Color.BLACK;
                    continue;
                }

                int r = buffer.get(offset) & 0xff;
                int g = buffer.get(offset + 1) & 0xff;
                int b = buffer.get(offset + 2) & 0xff;
                int a = buffer.get(offset + 3) & 0xff;

                pixels[y * ANALYSIS_WIDTH + x] =
                        Color.argb(a, r, g, b);
            }
        }

        small.setPixels(pixels, 0, ANALYSIS_WIDTH,
                0, 0, ANALYSIS_WIDTH, ANALYSIS_HEIGHT);

        try {
            boolean[] visited =
                    new boolean[ANALYSIS_WIDTH * ANALYSIS_HEIGHT];

            ArrayDeque<Integer> queue = new ArrayDeque<>();

            for (int start = 0; start < pixels.length; start++) {
                if (visited[start] || !isColorful(pixels[start])) {
                    continue;
                }

                int minX = ANALYSIS_WIDTH;
                int minY = ANALYSIS_HEIGHT;
                int maxX = 0;
                int maxY = 0;
                int count = 0;

                queue.clear();
                queue.add(start);
                visited[start] = true;

                while (!queue.isEmpty()) {
                    int index = queue.removeFirst();
                    int x = index % ANALYSIS_WIDTH;
                    int y = index / ANALYSIS_WIDTH;

                    count++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);

                    // Vecinos de arriba, abajo, izquierda y derecha.
                    int[] neighbors = {
                            x > 0 ? index - 1 : -1,
                            x < ANALYSIS_WIDTH - 1 ? index + 1 : -1,
                            y > 0 ? index - ANALYSIS_WIDTH : -1,
                            y < ANALYSIS_HEIGHT - 1
                                    ? index + ANALYSIS_WIDTH : -1
                    };

                    for (int next : neighbors) {
                        if (next >= 0 && !visited[next]
                                && isColorful(pixels[next])) {
                            visited[next] = true;
                            queue.addLast(next);
                        }
                    }

                    // Evita que una zona enorme consuma demasiado tiempo.
                    if (count > 5000) break;
                }

                int boxWidth = maxX - minX + 1;
                int boxHeight = maxY - minY + 1;

                // Filtro inicial experimental, no identifica personas.
                if (count >= 18 && count <= 1800
                        && boxWidth >= 3 && boxHeight >= 4
                        && boxWidth <= 70 && boxHeight <= 110) {

                    int centerX = (minX + maxX) / 2
                            * width / ANALYSIS_WIDTH;
                    int centerY = (minY + maxY) / 2
                            * height / ANALYSIS_HEIGHT;

                    results.add(new Detection(
                            centerX,
                            centerY,
                            boxWidth * width / ANALYSIS_WIDTH,
                            boxHeight * height / ANALYSIS_HEIGHT
                    ));
                }
            }
        } finally {
            small.recycle();
        }

        return results;
    }

    private boolean isColorful(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));

        // Excluye píxeles casi grises u oscuros.
        return max > 95 && (max - min) > 45;
    }

    private static class Detection {
        final int centerX;
        final int centerY;
        final int width;
        final int height;

        Detection(int centerX, int centerY, int width, int height) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;
        }
    }

    private void releaseCaptureResources() {
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (imageThread != null) {
            imageThread.quitSafely();
            imageThread = null;
            imageHandler = null;
        }

        processingImage = false;

        if (projection != null) {
            MediaProjection oldProjection = projection;
            projection = null;

            try {
                oldProjection.stop();
            } catch (RuntimeException e) {
                Log.w(TAG, "La captura ya estaba detenida", e);
            }
        }
    }

    private void stopCapture() {
        releaseCaptureResources();
        stopForeground(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Captura de pantalla",
                    NotificationManager.IMPORTANCE_LOW);

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("Scg-92 AutoClickerBB")
                .setContentText("Captura y análisis visual activos")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
