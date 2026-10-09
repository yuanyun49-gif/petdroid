package com.fish.petdroid;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/**
 * 只为拿一次录屏授权。
 * 圆子点开这个界面，系统的录屏确认框弹出来，她点允许，
 * 权限就交给 PetService，这个界面自己消失。
 */
public class CaptureActivity extends Activity {

    private static final int REQ = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            MediaProjectionManager m =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(), REQ);
        } catch (Throwable t) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            PetService.setProjection(resultCode, data);
            Intent svc = new Intent(this, PetService.class);
            startService(svc);
        }
        finish();
    }
}
