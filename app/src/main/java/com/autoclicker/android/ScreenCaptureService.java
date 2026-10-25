package com.autoclicker.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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
import android.util.Log;

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

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread imageThread;
    private Handler imageHandler;

    private boolean running = false;
    private long lastLogTime = 0L;

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
                EXTRA_RESULT_CODE,
                Activity.RESULT_CANCELED
        );

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
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
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
                    EXTRA_RESULT_DATA,
                    Intent.class
            );
        }

        return source.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private void startCapture(int resultCode, Intent resultData) {
        stopCapture();

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE
                );

        if (manager == null) {
            throw new IllegalStateException(
                    "MediaProjectionManager no disponible"
            );
        }

        projection = manager.getMediaProjection(resultCode, resultData);

        if (projection == null) {
            throw new IllegalStateException(
                    "No se pudo obtener MediaProjection"
            );
        }

        android.util.DisplayMetrics metrics =
                new android.util.DisplayMetrics();

        android.view.WindowManager windowManager =
                (android.view.WindowManager) getSystemService(
                        Context.WINDOW_SERVICE
                );

        if (windowManager == null) {
            throw new IllegalStateException(
                    "WindowManager no disponible"
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Rect bounds =
                    windowManager.getCurrentWindowMetrics().getBounds();

            metrics.widthPixels = bounds.width();
            metrics.heightPixels = bounds.height();
            metrics.densityDpi = getResources()
                    .getDisplayMetrics().densityDpi;
        } else {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        if (width <= 0 || height <= 0 || density <= 0) {
            throw new IllegalStateException(
                    "Dimensiones de pantalla inválidas"
            );
        }

        imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
        );

        imageThread = new HandlerThread("ScreenCaptureThread");
        imageThread.start();
        imageHandler = new Handler(imageThread.getLooper());

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;

            try {
                image = reader.acquireLatestImage();

                if (image != null) {
                    long now = android.os.SystemClock.elapsedRealtime();

                    // Registrar como máximo una vez cada 3 segundos.
                    if (now - lastLogTime >= 3000L) {
                        lastLogTime = now;

                        Log.d(
                                TAG,
                                "Captura recibida correctamente: "
                                        + image.getWidth()
                                        + " x "
                                        + image.getHeight()
                        );
                    }

                    // Próximo paso: analizar visualmente los personajes.
                    // Por ahora no se envían toques automáticos.

                }
            } catch (Exception e) {
                Log.e(TAG, "Error procesando una imagen", e);
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, imageHandler);

        projection.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.i(TAG, "Permiso de captura finalizado");
                        stopCapture();
                        stopSelf();
                    }
                },
                new Handler(getMainLooper())
        );

        virtualDisplay = projection.createVirtualDisplay(
                "AutoClickerScreen",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                imageHandler
        );

        if (virtualDisplay == null) {
            throw new IllegalStateException(
                    "No se pudo crear la pantalla virtual"
            );
        }

        running = true;
        Log.i(
                TAG,
                "Captura iniciada: " + width + " x " + height
        );
    }

    private void stopCapture() {
        running = false;

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

        if (projection != null) {
            MediaProjection oldProjection = projection;
            projection = null;

            try {
                oldProjection.stop();
            } catch (RuntimeException e) {
                Log.w(TAG, "La captura ya estaba detenida", e);
            }
        }

        stopForeground(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Captura de pantalla",
                    NotificationManager.IMPORTANCE_LOW
            );

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
                .setContentText("Captura de pantalla activa")
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
