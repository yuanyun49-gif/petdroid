package com.fish.petdroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.List;

public class PetService extends Service {

    private static final String CHANNEL_ID = "pet";
    private static final int NOTIFY_ID = 1001;
    private static final long POLL_MS = 2000L;
    private static final long BUBBLE_MS = 7000L;
    private static final long LOOK_MS = 25000L;

    private WindowManager wm;
    private LinearLayout petBox;
    private ImageView avatar;
    private TextView bubble;
    private WindowManager.LayoutParams params;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.List<Drawable> looks = new java.util.ArrayList<>();
    private int lookIndex = 0;

    private String currentPkg = "";
    private int messageIndex = 0;
    private long enteredAt = 0L;
    private String lastLine = "";
    private float dens = 1f;
    private float bobPhase = 0f;
    private boolean bobOn = true;

    private final Runnable bob = new Runnable() {
        @Override
        public void run() {
            if (avatar != null && bobOn) {
                bobPhase += 0.075f;
                float amp = 7f * dens;
                avatar.setTranslationY((float) Math.sin(bobPhase) * amp);
                float s = 1f + 0.015f * (float) Math.cos(bobPhase * 2);
                avatar.setScaleX(s);
                avatar.setScaleY(s);
            }
            handler.postDelayed(this, 33);
        }
    };

    private final Runnable marquee = new Runnable() {
        @Override
        public void run() {
            if (bubble == null || bubble.getVisibility() != View.VISIBLE) {
                return;
            }
            try {
                CharSequence cs = bubble.getText();
                if (cs == null) {
                    return;
                }
                float text = bubble.getPaint().measureText(cs.toString());
                int limit = (int) (text + bubble.getPaddingLeft() + bubble.getPaddingRight() - bubble.getWidth());
                if (limit > (int) (8 * dens)) {
                    int x = bubble.getScrollX() + (int) (2 * dens);
                    if (x > limit + (int) (12 * dens)) {
                        bubble.scrollTo(0, 0);
                        handler.postDelayed(this, 900);
                        return;
                    }
                    bubble.scrollTo(x, 0);
                }
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, 30);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                checkForeground();
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIFY_ID, buildNotification());
        setupOverlay();
        handler.postDelayed(tick, POLL_MS);
        handler.postDelayed(bob, 200);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(switchLook);
        handler.removeCallbacks(bob);
        handler.removeCallbacks(marquee);
        handler.removeCallbacks(hideBubble);
        if (wm != null && petBox != null) {
            try {
                wm.removeView(petBox);
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "桌宠", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("桌宠在看着你")
                .setContentText("蹲着呢")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }

    private void setupOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40;
        params.y = 300;
        float d = getResources().getDisplayMetrics().density;
        dens = d;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int bobPx = (int) (9 * d);

        petBox = new LinearLayout(this);
        petBox.setOrientation(LinearLayout.VERTICAL);
        petBox.setGravity(Gravity.CENTER_HORIZONTAL);
        petBox.setPadding(0, bobPx, 0, bobPx);
        petBox.setClipChildren(false);
        petBox.setClipToPadding(false);

        bubble = new TextView(this);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(13);
        bubble.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        bubble.setBackgroundColor(0xCC222222);
        bubble.setSingleLine(true);
        bubble.setHorizontallyScrolling(true);
        bubble.setEllipsize(null);
        bubble.setMaxWidth((int) (screenW * 0.68));
        bubble.setVisibility(View.GONE);
        petBox.addView(bubble);

        avatar = new ImageView(this);
        int size = (int) (96 * d);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        loadLooks(d);
        avatar.setImageDrawable(looks.get(0));
        petBox.addView(avatar);


        avatar.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true;
                        }
                        params.x = startX + dx;
                        params.y = startY + dy;
                        wm.updateViewLayout(petBox, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            say("别戳我");
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        wm.addView(petBox, params);
        if (looks.size() > 1) {
            handler.postDelayed(switchLook, LOOK_MS);
        }
    }

    private final Runnable switchLook = new Runnable() {
        @Override
        public void run() {
            if (looks.size() > 1 && avatar != null) {
                lookIndex = (lookIndex + 1) % looks.size();
                avatar.setImageDrawable(looks.get(lookIndex));
                avatar.setAlpha(0.25f);
                avatar.animate().alpha(1f).setDuration(300).start();
            }
            handler.postDelayed(this, LOOK_MS);
        }
    };

    private Drawable loadAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            Bitmap bm = BitmapFactory.decodeStream(in);
            if (bm != null) {
                return new android.graphics.drawable.BitmapDrawable(getResources(), bm);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void loadLooks(float d) {
        looks.clear();
        for (int i = 1; i <= 6; i++) {
            Drawable dr = loadAsset(i == 1 ? "pet.png" : "pet" + i + ".png");
            if (dr != null) {
                looks.add(dr);
            }
        }
        if (looks.isEmpty()) {
            looks.add(fallbackCircle(d));
        }
    }

    private Drawable fallbackCircle(float d) {
        int size = (int) (96 * d);
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF7FB3D5);
        c.drawCircle(size / 2f, size / 2f, size / 2f - 2, p);
        p.setColor(Color.WHITE);
        c.drawCircle(size * 0.36f, size * 0.42f, size * 0.07f, p);
        c.drawCircle(size * 0.64f, size * 0.42f, size * 0.07f, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bm);
    }

    private void logLine(String text) {
        try {
            java.io.File f = new java.io.File("/sdcard/Download/Operit/pet_log.txt");
            if (f.exists() && f.length() > 300 * 1024) {
                f.delete();
            }
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            String ts = new java.text.SimpleDateFormat("MM-dd HH:mm:ss",
                    java.util.Locale.US).format(new java.util.Date());
            w.write(ts + "  " + text + "\n");
            w.close();
        } catch (Throwable ignored) {
        }
    }

    private void say(String text) {
        logLine(text);
        bubble.setText(text);
        bubble.scrollTo(0, 0);
        bubble.setVisibility(View.VISIBLE);
        bubble.setAlpha(0f);
        bubble.setTranslationY(-6 * dens);
        bubble.animate().alpha(1f).translationY(0f).setDuration(180).start();
        handler.removeCallbacks(hideBubble);
        handler.removeCallbacks(marquee);
        handler.postDelayed(marquee, 800);
        handler.postDelayed(hideBubble, BUBBLE_MS);
    }

    private final Runnable hideBubble = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(marquee);
            bubble.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
                @Override
                public void run() {
                    bubble.setVisibility(View.GONE);
                    bubble.setAlpha(1f);
                    bubble.scrollTo(0, 0);
                }
            }).start();
        }
    };

    private void checkForeground() {
        String top = topPackage();
        if (top == null || top.isEmpty()) {
            return;
        }
        if (!top.equals(currentPkg)) {
            currentPkg = top;
            messageIndex = 0;
            enteredAt = System.currentTimeMillis();
            return;
        }
        if (getPackageName().equals(currentPkg)) {
            return;
        }

        long stayed = System.currentTimeMillis() - enteredAt;
        long trigger = 8000L + (long) messageIndex * 20000L;
        if (stayed < trigger) {
            return;
        }
        int minutes = (int) (stayed / 60000L);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String line = AppMessages.next(this, currentPkg, minutes, hour, batteryPercent(), lastLine);
        if (line != null) {
            say(line);
            lastLine = line;
            messageIndex++;
        }
    }

    private int batteryPercent() {
        try {
            android.content.Intent i = registerReceiver(null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int lvl = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                if (lvl > 0 && scale > 0) {
                    return Math.round(lvl * 100f / scale);
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private String topPackage() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long begin = now - 3600_000L;

        String best = null;
        long bestTime = 0L;

        List<UsageStats> list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now);
        if (list != null) {
            for (UsageStats s : list) {
                if (s.getLastTimeUsed() > bestTime) {
                    bestTime = s.getLastTimeUsed();
                    best = s.getPackageName();
                }
            }
        }

        if (best == null) {
            try {
                UsageEvents events = usm.queryEvents(begin, now);
                if (events != null) {
                    UsageEvents.Event e = new UsageEvents.Event();
                    long t = 0L;
                    while (events.hasNextEvent()) {
                        events.getNextEvent(e);
                        if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND
                                || e.getEventType() == 1) {
                            if (e.getTimeStamp() > t) {
                                t = e.getTimeStamp();
                                best = e.getPackageName();
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        android.util.Log.d("PetDroid", "top=" + best);
        return best;
    }
}