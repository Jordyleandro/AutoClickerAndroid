package com.autoclicker.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {

    private boolean permissionRequestLaunched = false;
    private boolean panelStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // No se muestra una pantalla de menú: se abre directamente el panel.
        if (Settings.canDrawOverlays(this)) {
            openFloatingPanel();
        } else {
            permissionRequestLaunched = true;
            Intent permissionIntent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(permissionIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Al volver de los ajustes, abre el panel automáticamente.
        if (permissionRequestLaunched && !panelStarted) {
            if (Settings.canDrawOverlays(this)) {
                openFloatingPanel();
            } else {
                finish();
            }
        }
    }

    private void openFloatingPanel() {
        if (panelStarted) return;

        if (!Settings.canDrawOverlays(this)) {
            finish();
            return;
        }

        panelStarted = true;
        startService(new Intent(this, FloatingPanelService.class));
        finish();
    }
}
