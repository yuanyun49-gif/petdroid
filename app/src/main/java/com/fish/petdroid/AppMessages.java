package com.fish.petdroid;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 桌宠说的话。
 * 不是固定循环的那几句，而是按她此刻的状态现场挑一条：在这个应用里待了多久、
 * 现在几点、手机还剩多少电。同一句不会连着说两遍。
 *
 * 另外留了一个后门：把 JSON 放到
 * Android/data/com.fish.petdroid/files/pet_lines.json
 * 就能在外面随时给它加新台词，不用重新打包。
 * 结构：{"any":[],"night":[],"lowBattery":[],"arrive":{"包名":[]},"stay":{"包名":[]}}
 * 台词里可以用 {minutes} {hour} {battery} 三个占位符。
 */
public class AppMessages {

    private static final Map<String, String[]> ARRIVE = new HashMap<>();
    private static final Map<String, String[]> STAY = new HashMap<>();
    private static final Random RND = new Random();

    private static final String[] ANY = {
            "抬头看我一眼",
            "歇两分钟呗，就两分钟",
            "我在这儿蹲着呢",
            "别一直低着头，脖子要抗议了",
            "说句话呗，随便什么都行",
            "眼睛该看远处了，就十秒钟",
            "手边那杯水是不是还没喝",
            "你在忙什么，我好奇",
            "我又没催你，就是想让你知道我在",
            "屏幕不会跑，我也不会",
            "刚才那条消息我看了，没回你，是想让你先抬头",
            "手机拿这么久，手不酸吗",
    };

    private static final String[] NIGHT = {
            "这个点还不睡，明天有你难受的",
            "夜里盯着屏幕，眼睛明天会找你算账",
            "该睡了，我说真的",
            "这么晚了，屋里就剩你一个还醒着",
            "再不睡，明天起来又得赖床",
    };

    private static final String[] LOW_BATTERY = {
            "电量{battery}%，先把线插上",
            "快没电了，充电器呢",
            "{battery}%了，一会儿黑屏别怪我没提醒",
            "插上电再玩，听话",
    };

    static {
        ARRIVE.put("com.ss.android.ugc.aweme", new String[]{
                "又来了",
                "就知道你会点开这个",
                "这次打算看多久",
                "第三条还是第一百条",
                "手指头滑得挺快",
        });
        STAY.put("com.ss.android.ugc.aweme", new String[]{
                "在抖音里待了{minutes}分钟了",
                "{minutes}分钟，够看两部电影了",
                "你已经刷了{minutes}分钟，自己信吗",
                "眼睛干不干，你自己说",
                "{minutes}分钟了，刷到什么好东西，也不给我看",
                "再刷下去，{hour}点就要变成你睡觉的时间了",
        });

        ARRIVE.put("com.netease.dwrg", new String[]{
                "开了？",
                "又排一把",
                "上一把赢了还是输了，别憋着",
        });
        STAY.put("com.netease.dwrg", new String[]{
                "打{minutes}分钟了，输赢都歇一下",
                "手速练出来了，眼睛也快撑不住了",
                "这一把打完，回来看看我",
                "{minutes}分钟，我数着呢",
        });

        ARRIVE.put("com.tencent.mm", new String[]{
                "在跟谁说话",
                "对话框点得挺勤",
                "聊什么呢，笑得那么轻",
        });
        STAY.put("com.tencent.mm", new String[]{
                "聊了{minutes}分钟了，手不累吗",
                "{minutes}分钟，话题还挺长",
                "打字这么久，谁让你这么有耐心",
        });

        ARRIVE.put("com.tencent.mobileqq", new String[]{
                "QQ在响",
                "又是谁，这么热闹",
        });
        STAY.put("com.tencent.mobileqq", new String[]{
                "在QQ里待了{minutes}分钟了",
                "{minutes}分钟，群里聊得挺开心啊",
        });

        ARRIVE.put("com.ai.assistance.operit", new String[]{
                "我在这儿呢",
                "又来找我了",
                "这次要我做什么",
        });
        STAY.put("com.ai.assistance.operit", new String[]{
                "盯着这些字{minutes}分钟了，眼睛真的会疼",
                "歇会儿再弄，我又不会跑",
                "{minutes}分钟了，喝口水吧",
        });

        ARRIVE.put("com.microsoft.emmx", new String[]{
                "看什么呢",
                "翻资料呢，还是刷别的",
        });
        STAY.put("com.microsoft.emmx", new String[]{
                "看了{minutes}分钟了",
                "{minutes}分钟，看到什么了",
        });
    }

    public static String next(Context ctx, String pkg, int minutes, int hour, int battery, String avoid) {
        List<String> pool = new ArrayList<>();

        boolean lowBattery = battery > 0 && battery <= 15;
        boolean night = hour >= 1 && hour < 6;
        boolean longStay = minutes >= 40;

        if (lowBattery) {
            addAll(pool, LOW_BATTERY, 1);
        }
        if (night) {
            addAll(pool, NIGHT, 1);
        }
        if (longStay) {
            addAll(pool, STAY.get(pkg), 3);
        } else {
            addAll(pool, ARRIVE.get(pkg), 2);
        }
        addAll(pool, ANY, 1);

        JSONObject extra = load(ctx);
        if (extra != null) {
            addJson(pool, extra, "any", 1);
            if (night) {
                addJson(pool, extra, "night", 1);
            }
            if (lowBattery) {
                addJson(pool, extra, "lowBattery", 1);
            }
            JSONObject byApp = extra.optJSONObject(longStay ? "stay" : "arrive");
            if (byApp != null) {
                addJson(pool, byApp, pkg, 3);
            }
        }

        if (pool.isEmpty()) {
            return null;
        }
        String picked = null;
        for (int i = 0; i < 14; i++) {
            String c = pool.get(RND.nextInt(pool.size()));
            if (!c.equals(avoid)) {
                picked = c;
                break;
            }
        }
        if (picked == null) {
            picked = pool.get(RND.nextInt(pool.size()));
        }
        return fill(picked, minutes, hour, battery);
    }

    private static void addAll(List<String> pool, String[] arr, int weight) {
        if (arr == null) {
            return;
        }
        for (String s : arr) {
            if (s != null && s.length() > 0) {
                for (int i = 0; i < weight; i++) {
                    pool.add(s);
                }
            }
        }
    }

    private static void addJson(List<String> pool, JSONObject obj, String key, int weight) {
        JSONArray arr = obj.optJSONArray(key);
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "");
            if (s.length() > 0) {
                for (int k = 0; k < weight; k++) {
                    pool.add(s);
                }
            }
        }
    }

    private static String fill(String s, int minutes, int hour, int battery) {
        return s.replace("{minutes}", String.valueOf(minutes))
                .replace("{hour}", String.valueOf(hour))
                .replace("{battery}", String.valueOf(battery));
    }

    private static JSONObject load(Context ctx) {
        JSONObject o = read(new File("/sdcard/Download/Operit/pet_lines.json"));
        if (o != null) {
            return o;
        }
        File dir = ctx.getExternalFilesDir(null);
        if (dir != null) {
            return read(new File(dir, "pet_lines.json"));
        }
        return null;
    }

    private static JSONObject read(File f) {
        try {
            if (!f.exists() || f.length() == 0 || f.length() > 512 * 1024) {
                return null;
            }
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return null;
        }
    }
}