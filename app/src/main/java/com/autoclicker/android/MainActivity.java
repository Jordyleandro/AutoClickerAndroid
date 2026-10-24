
package com.autoclicker.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("AutoClickerNuevo");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

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
            }
        });
        layout.addView(overlay);

        Button open = new Button(this);
        open.setText("Abrir panel");
        open.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) {
                startService(new Intent(
                    this,
                    FloatingPanelService.class
                ));
            } else {
                startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                ));
            }
        });
        layout.addView(open);

        setContentView(layout);
    }
}
