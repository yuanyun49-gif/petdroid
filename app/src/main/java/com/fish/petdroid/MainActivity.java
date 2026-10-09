package com.fish.petdroid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView tip = new TextView(this);
        tip.setText("两个权限都得给。不给悬浮窗，它没地方站；不给使用情况访问，它不知道你在刷什么。");
        tip.setTextSize(15);
        root.addView(tip);

        root.addView(button("1. 允许悬浮窗", v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }, d));

        root.addView(button("2. 允许使用情况访问", v -> {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }, d));

        root.addView(button("叫它出来", v -> {
            Intent i = new Intent(this, PetService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        }, d));

        root.addView(button("让它回去睡", v -> {
            stopService(new Intent(this, PetService.class));
        }, d));

        setContentView(root);
        try {
            Intent i = new Intent(this, PetService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    private Button button(String label, View.OnClickListener l, float d) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (12 * d);
        b.setLayoutParams(lp);
        return b;
    }
}