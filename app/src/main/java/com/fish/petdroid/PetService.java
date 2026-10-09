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
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
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
                            pokeBack();
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
        say(text, 0);
    }

    private void say(String text, int bubbleMs) {
        lastSayAt = System.currentTimeMillis();
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
        handler.postDelayed(hideBubble, bubbleMs > 0 ? bubbleMs : BUBBLE_MS);
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

    private long lastSayAt = 0L;
    private int lastBatt = -1;
    private int lastNightHour = -1;
    private int nextStayMark = 15;

    private boolean quiet() {
        return System.currentTimeMillis() - lastSayAt < 45000L;
    }

    private int hourNow() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
    }

    private int nextMark(int m) {
        if (m < 20) {
            return 30;
        }
        if (m < 30) {
            return 45;
        }
        if (m < 45) {
            return 60;
        }
        if (m < 60) {
            return 90;
        }
        if (m < 90) {
            return 120;
        }
        return m + 60;
    }

    private void checkPush() {
        try {
            java.io.File f = new java.io.File("/sdcard/Download/Operit/pet_now.json");
            if (!f.exists() || f.length() == 0) {
                return;
            }
            byte[] buf = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(buf);
            in.close();
            if (n <= 0) {
                return;
            }
            org.json.JSONObject o = new org.json.JSONObject(new String(buf, 0, n, "UTF-8"));
            String line = o.optString("line", "");
            if (line.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            long atMs = now;
            String at = o.optString("at", "");
            if (at.indexOf(':') > 0) {
                String[] hm = at.split(":");
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0].trim()));
                c.set(java.util.Calendar.MINUTE, Integer.parseInt(hm[1].trim()));
                c.set(java.util.Calendar.SECOND, 0);
                atMs = c.getTimeInMillis();
            }
            int expire = o.optInt("expire_minutes", 10);
            if (now > atMs + expire * 60000L) {
                f.delete();
                return;
            }
            if (now < atMs - 2000L) {
                return;
            }
            say(line, o.optInt("bubble_seconds", 0) * 1000);
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    private long lastThinkAt = 0L;

    private long lastPokeThinkAt = 0L;

    private void pokeBack() {
        if (PetBrain.ready()) {
            long now = System.currentTimeMillis();
            if (now - lastPokeThinkAt >= 12000L) {
                lastPokeThinkAt = now;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String line = PetBrain.think(
                                "她刚刚用手指戳了你一下，人就看着屏幕。", 7000);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (line != null) {
                                    PetBrain.remember(line);
                                    say(line, 0);
                                } else {
                                    fallbackPoke();
                                }
                            }
                        });
                    }
                }).start();
                return;
            }
        }
        fallbackPoke();
    }

    private void fallbackPoke() {
        String p = AppMessages.poke(this);
        say(p != null ? p : "别戳我");
    }

    private void utterance(final String kind, final int minutes, final int batt) {
        if (PetBrain.ready()) {
            long now = System.currentTimeMillis();
            if (now - lastThinkAt < ("watch".equals(kind) ? 170000L : 240000L)) {
                return;
            }
            lastThinkAt = now;
            final String state = describe(kind, minutes, batt);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String line = PetBrain.think(state);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (line != null) {
                                PetBrain.remember(line);
                                say(line, 0);
                            } else {
                                fallback(kind, minutes, batt);
                            }
                        }
                    });
                }
            }).start();
            return;
        }
        fallback(kind, minutes, batt);
    }

    private void fallback(String kind, int minutes, int batt) {
        String line = AppMessages.next(this, currentPkg, minutes, hourNow(), batt, lastLine, kind);
        if (line != null) {
            say(line, 0);
            lastLine = line;
        }
    }

    private String describe(String kind, int minutes, int batt) {
        StringBuilder sb = new StringBuilder();
        sb.append("现在时间")
                .append(new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                        .format(new java.util.Date()))
                .append("。");
        sb.append("她在").append(appName(currentPkg));
        if (minutes > 0) {
            sb.append("里待了").append(minutes).append("分钟");
        }
        sb.append("。");
        if (batt > 0) {
            sb.append("电量").append(batt).append("%。");
        }
        if ("arrive".equals(kind)) {
            sb.append("她刚刚打开这个应用。");
        } else if ("stay".equals(kind)) {
            sb.append("她在同一个应用里坐了很久了。");
        } else if ("night".equals(kind)) {
            sb.append("现在是深夜。");
        } else if ("low".equals(kind)) {
            sb.append("手机快没电了。");
        } else if ("watch".equals(kind)) {
            sb.append("她正和你一起刷抖音，把你也当成坐在旁边的人。"
                    + "说一句此刻的想法、吐槽或者短评，二十个字以内，别催她睡觉别催充电。");
        }
        return sb.toString();
    }

    private String appName(String pkg) {
        if (pkg == null) {
            return "手机";
        }
        switch (pkg) {
            case "com.ss.android.ugc.aweme":
                return "抖音";
            case "com.netease.dwrg":
                return "第五人格";
            case "com.tencent.mm":
                return "微信";
            case "com.microsoft.emmx":
                return "浏览器";
            case "com.ai.assistance.operit":
                return "Operit，就是和我聊天的那个界面";
            case "com.xingin.xhs":
                return "小红书";
            case "com.tencent.mobileqq":
                return "QQ";
            case "tv.danmaku.bili":
                return "B站";
            default:
                return "一个应用";
        }
    }

    private long lastWatchAt = 0L;
    private boolean watchOn = false;

    private void refreshWatch() {
        try {
            java.io.File f = new java.io.File("/sdcard/Download/Operit/pet_watch.json");
            if (!f.exists() || f.length() == 0 || f.length() > 8192) {
                watchOn = false;
                return;
            }
            byte[] buf = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(buf);
            in.close();
            if (n <= 0) {
                watchOn = false;
                return;
            }
            watchOn = new org.json.JSONObject(new String(buf, 0, n, "UTF-8")).optBoolean("enabled", false);
        } catch (Throwable ignored) {
            watchOn = false;
        }
    }

    private static int projResult = 0;
    private static Intent projData = null;

    public static void setProjection(int result, Intent data) {
        projResult = result;
        projData = data;
    }

    private MediaProjection projection;
    private ImageReader capReader;
    private VirtualDisplay capDisplay;
    private int capW = 0;
    private int capH = 0;

    private void ensureCapture() {
        if (projection != null || projData == null) {
            return;
        }
        try {
            Point size = new Point();
            WindowManager w = (WindowManager) getSystemService(WINDOW_SERVICE);
            w.getDefaultDisplay().getRealSize(size);
            float s = Math.min(1f, 420f / Math.max(1, size.x));
            capW = Math.max(200, (int) (size.x * s));
            capH = Math.max(360, (int) (size.y * s));
            capReader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2);
            MediaProjectionManager m =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = m.getMediaProjection(projResult, projData);
            if (projection == null) {
                return;
            }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    projection = null;
                }
            }, handler);
            capDisplay = projection.createVirtualDisplay("fishcap", capW, capH, 320,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    capReader.getSurface(), null, handler);
            logLine("录屏接上了 " + capW + "x" + capH);
        } catch (Throwable t) {
            projection = null;
            logLine("录屏失败 " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    private String grabJpeg() {
        ensureCapture();
        if (projection == null || capReader == null) {
            return null;
        }
        Image img = null;
        for (int i = 0; i < 12 && img == null; i++) {
            img = capReader.acquireLatestImage();
            if (img == null) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (img == null) {
            return null;
        }
        try {
            Image.Plane p = img.getPlanes()[0];
            java.nio.ByteBuffer buf = p.getBuffer();
            int rowStride = p.getRowStride();
            int pixStride = p.getPixelStride();
            int w = rowStride / pixStride;
            Bitmap full = Bitmap.createBitmap(w, capH, Bitmap.Config.ARGB_8888);
            buf.rewind();
            full.copyPixelsFromBuffer(buf);
            Bitmap cut = (w > capW) ? Bitmap.createBitmap(full, 0, 0, capW, capH) : full;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            cut.compress(Bitmap.CompressFormat.JPEG, 60, bos);
            return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            logLine("截图失败 " + t.getClass().getSimpleName());
            return null;
        } finally {
            img.close();
        }
    }

    private void watchLook(final int minutes, final int batt) {
        if (PetBrain.ready()) {
            long now = System.currentTimeMillis();
            if (now - lastThinkAt < 90000L) {
                return;
            }
            lastThinkAt = now;
            final String state = describe("watch", minutes, batt);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String jpeg = grabJpeg();
                    final String line = (jpeg == null) ? null
                            : PetBrain.thinkImage(state, jpeg);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (line != null) {
                                PetBrain.remember(line);
                                say(line, 0);
                            } else if (jpeg == null) {
                                fallback("watch", minutes, batt);
                            }
                        }
                    });
                }
            }).start();
        } else {
            fallback("watch", minutes, batt);
        }
    }

    private void checkForeground() {
        refreshWatch();
        checkPush();
        String top = topPackage();
        if (top == null || top.isEmpty()) {
            return;
        }
        if (getPackageName().equals(top)) {
            return;
        }
        if (!top.equals(currentPkg)) {
            currentPkg = top;
            enteredAt = System.currentTimeMillis();
            nextStayMark = 15;
            if (!quiet()) {
                utterance("arrive", 0, batteryPercent());
            }
            return;
        }

        long stayed = System.currentTimeMillis() - enteredAt;
        int minutes = (int) (stayed / 60000L);
        int batt = batteryPercent();

        if (minutes >= nextStayMark && !quiet()) {
            utterance("stay", minutes, batt);
            nextStayMark = nextMark(nextStayMark);
        }

        if (lastNightHour != hourNow()) {
            lastNightHour = hourNow();
            if (lastNightHour >= 1 && lastNightHour < 6 && !quiet()) {
                utterance("night", minutes, batt);
            }
        }

        if (batt > 0 && batt <= 15 && lastBatt > 15 && !quiet()) {
            utterance("low", minutes, batt);
        }

        if (watchOn && "com.ss.android.ugc.aweme".equals(currentPkg) && minutes >= 2
                && System.currentTimeMillis() - lastWatchAt >= 120000L && !quiet()) {
            lastWatchAt = System.currentTimeMillis();
            watchLook(minutes, batt);
        }
        if (batt > 0) {
            lastBatt = batt;
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