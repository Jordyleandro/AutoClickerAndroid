package com.autoclicker.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String ACTION_START_DETECTION =
            "com.autoclicker.android.START_DETECTION";

    private static final int REQUEST_SCREEN_CAPTURE = 902;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createInterface();
        handleDetectionRequest(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDetectionRequest(intent);
    }

    private void createInterface() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("Scg-92 AutoClickerBB");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        status = new TextView(this);
        status.setText("Estado: listo");
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 20, 0, 20);
        layout.addView(status);

        Button accessibility = new Button(this);
        accessibility.setText("Activar accesibilidad");
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                ))
        );
        layout.addView(accessibility);

        Button overlay = new Button(this);
        overlay.setText("Permitir panel flotante");
        overlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            } else {
                Toast.makeText(this,
                        "El permiso del panel ya está concedido",
                        Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(overlay);

        Button open = new Button(this);
        open.setText("Abrir panel");
        open.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) {
                startService(new Intent(
                        this, FloatingPanelService.class
                ));
            } else {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                ));
            }
        });
        layout.addView(open);

        Button startDetection = new Button(this);
        startDetection.setText("Iniciar detección");
        startDetection.setOnClickListener(v -> requestScreenCapture());
        layout.addView(startDetection);

        Button stopDetection = new Button(this);
        stopDetection.setText("Detener detección");
        stopDetection.setOnClickListener(v -> stopScreenCapture());
        layout.addView(stopDetection);

        setContentView(layout);
    }

    private void handleDetectionRequest(Intent intent) {
        if (intent == null) return;

        if (ACTION_START_DETECTION.equals(intent.getAction())) {
            intent.setAction(null);
            requestScreenCapture();
        }
    }

    private void requestScreenCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE
                );

        if (manager == null) {
            status.setText("Error: captura no disponible");
            return;
        }

        status.setText("Esperando permiso de Android...");

        Intent captureIntent = manager.createScreenCaptureIntent();
        startActivityForResult(
                captureIntent,
                REQUEST_SCREEN_CAPTURE
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_SCREEN_CAPTURE) return;

        if (resultCode != RESULT_OK || data == null) {
            status.setText("Captura cancelada: falta autorización");
            return;
        }

        Intent serviceIntent = new Intent(
                this, ScreenCaptureService.class
        );
        serviceIntent.setAction(ScreenCaptureService.ACTION_START);
        serviceIntent.putExtra(
                ScreenCaptureService.EXTRA_RESULT_CODE,
                resultCode
        );
        serviceIntent.putExtra(
                ScreenCaptureService.EXTRA_RESULT_DATA,
                data
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            status.setText("Captura iniciada; vuelve al juego.");
        } catch (Exception e) {
            status.setText(
                    "No se pudo iniciar la captura: " + e.getMessage()
            );
        }
    }

    private void stopScreenCapture() {
        Intent stopIntent = new Intent(
                this, ScreenCaptureService.class
        );
        stopIntent.setAction(ScreenCaptureService.ACTION_STOP);

        try {
            startService(stopIntent);
            status.setText("Solicitud de detener enviada");
        } catch (Exception e) {
            status.setText(
                    "No se pudo detener: " + e.getMessage()
            );
        }
    }
}
