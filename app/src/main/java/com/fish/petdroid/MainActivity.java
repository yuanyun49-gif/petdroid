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

    private Button capBtn;

    @Override
    protected void onResume() {
        super.onResume();
        if (capBtn != null) {
            capBtn.setText(PetService.hasProjection()
                    ? "3. 录屏已给"
                    : "3. 允许录屏（陪你看抖音）");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView tip = new TextView(this);
        tip.setText("三个权限都给齐，它才好用。不给悬浮窗，它没地方站；不给使用情况访问，它不知道你在刷什么；不给录屏，它看不到你的屏幕，陪你看抖音的时候只能瞎猜。");
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

        capBtn = button("3. 允许录屏（陪你看抖音）", v -> {
            startActivity(new Intent(this, CaptureActivity.class));
        }, d);
        root.addView(capBtn);

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