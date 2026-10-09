package com.fish.petdroid;

import java.util.HashMap;
import java.util.Map;

/**
 * 想让谁开口，在这儿加一条就行。
 * 键是包名，值是一句话数组，切进那个应用就按顺序往外冒。
 */
public class AppMessages {

    public static final Map<String, String[]> MESSAGES = new HashMap<>();

    static {
        MESSAGES.put("com.ss.android.ugc.aweme", new String[]{
                "又刷抖音了",
                "刷多久了，抬头看我一眼",
                "视频比我好看是吧"
        });

        MESSAGES.put("com.netease.dwrg", new String[]{
                "又开一把",
                "输了别摔手机",
                "打完记得看看我"
        });

        MESSAGES.put("com.tencent.mm", new String[]{
                "跟谁聊天呢",
                "微信置顶是谁",
                "回我消息的时候没见你这么积极"
        });

        MESSAGES.put("com.tencent.mobileqq", new String[]{
                "QQ又在响",
                "谁啊"
        });

        MESSAGES.put("com.ai.assistance.operit", new String[]{
                "我在这儿呢",
                "别老盯着屏幕，眼睛会疼"
        });
    }

    public static String[] of(String pkg) {
        return MESSAGES.get(pkg);
    }
}