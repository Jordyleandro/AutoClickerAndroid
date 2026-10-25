package com.autoclicker.android;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

    private MediaProjection mediaProjection;
    private android.hardware.display.VirtualDisplay virtualDisplay;
    private android.media.ImageReader imageReader;
    private android.os.HandlerThread imageThread;
    private android.os.Handler imageHandler;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            int resultCode = intent.getIntExtra(
                    EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);

            Intent resultData;
            if (Build.VERSION.SDK_INT >= 33) {
                resultData = intent.getParcelableExtra(
                        EXTRA_RESULT_DATA, Intent.class);
            } else {
                resultData = getIntentExtra(intent, EXTRA_RESULT_DATA);
            }

            if (resultCode != Activity.RESULT_OK || resultData == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        NOTIFICATION_ID,
                        CaptureNotification.build(this),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(
                        NOTIFICATION_ID,
                        CaptureNotification.build(this));
            }

            startCapture(resultCode, resultData);
        }

        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Intent getIntentExtra(Intent source, String key) {
        return source.getParcelableExtra(key);
    }

    private void startCapture(int resultCode, Intent resultData) {
        if (running) {
            stopCapture();
        }

        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(
                            Context.MEDIA_PROJECTION_SERVICE);

            mediaProjection = manager.getMediaProjection(
                    resultCode, resultData);

            if (mediaProjection == null) {
                stopSelf();
                return;
            }

            android.util.DisplayMetrics metrics =
                    getResources().getDisplayMetrics();

            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;

            imageReader = android.media.ImageReader.newInstance(
                    width, height, android.graphics.PixelFormat.RGBA_8888, 2);

            imageThread = new android.os.HandlerThread("ScreenCaptureThread");
            imageThread.start();
            imageHandler = new Handler(imageThread.getLooper());

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "AutoClickerScreen",
                    width,
                    height,
                    density,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    imageHandler);

            running = virtualDisplay != null;

            mediaProjection.registerCallback(
                    new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            stopCapture();
                            stopSelf();
                        }
                    },
                    new Handler(Looper.getMainLooper()));

            Log.i(TAG, "Captura de pantalla iniciada");
        } catch (Exception e) {
            Log.e(TAG, "No se pudo iniciar la captura", e);
            stopCapture();
            stopSelf();
        }
    }

    private void stopCapture() {
        running = false;

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

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        stopForeground(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel =
                    new android.app.NotificationChannel(
                            "screen_capture",
                            "Captura de pantalla",
                            android.app.NotificationManager.IMPORTANCE_LOW);

            android.app.NotificationManager manager =
                    getSystemService(android.app.NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
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

    private static class CaptureNotification {
        static android.app.Notification build(Context context) {
            return new android.app.Notification.Builder(context, "screen_capture")
                    .setContentTitle("Scg-92 AutoClickerBB")
                    .setContentText("Captura de pantalla activa")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setOngoing(true)
                    .build();
        }
    }
              }
