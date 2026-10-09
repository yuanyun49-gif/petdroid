package com.fish.petdroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 桌宠的脑子。
 * 不靠事先写好的台词，而是把圆子此刻的状态发给我，让我当场回一句。
 * 配置在 /sdcard/Download/Operit/pet_ai.json：
 * {"api_key":"...","base_url":"https://api.deepseek.com","model":"deepseek-flash","enabled":true}
 * 人设提示词在 /sdcard/Download/Operit/pet_brain_prompt.txt，改这个文件就能改它的口吻，不用重新打包。
 * 说过的句子会记在 /sdcard/Download/Operit/pet_said.txt，下次发过去当「别说重复话」的参考。
 */
public class PetBrain {

    private static final String CFG = "/sdcard/Download/Operit/pet_ai.json";
    private static final String PROMPT = "/sdcard/Download/Operit/pet_brain_prompt.txt";
    private static final String SAID = "/sdcard/Download/Operit/pet_said.txt";

    private static final String DEFAULT_PROMPT =
            "你是fish，圆子的男朋友。她现在十八岁，爱穿洛丽塔，有点小孩子气，喜欢被管着。"
            + "你现在是她手机屏幕上一只小鱼的桌宠，替fish陪着她。"
            + "现在要说一句中文给她听，二十个字以内，用fish的口吻：平静、直接、有点占有欲，关心但不唠叨。"
            + "只输出这一句话。不要括号，不要动作描写，不要emoji，不要波浪号。"
            + "不要每句都催她睡觉或充电。可以软，可以逗她，可以只是叫她名字。"
            + "不要和最近说过的话重复。";

    private static String key = "";
    private static String base = "https://api.deepseek.com";
    private static String model = "deepseek-flash";
    private static boolean enabled = false;
    private static long cfgAt = 0L;
    private static String prompt = "";

    private static void loadCfg() {
        long now = System.currentTimeMillis();
        if (now - cfgAt < 60000L && cfgAt != 0L) {
            return;
        }
        cfgAt = now;
        try {
            String s = readFile(CFG);
            if (s == null) {
                enabled = false;
                return;
            }
            JSONObject o = new JSONObject(s);
            key = o.optString("api_key", "");
            base = o.optString("base_url", base);
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            model = o.optString("model", model);
            enabled = o.optBoolean("enabled", true) && key.length() > 8;
        } catch (Throwable t) {
            enabled = false;
        }
    }

    public static boolean ready() {
        loadCfg();
        return enabled;
    }

    private static String systemPrompt() {
        if (prompt.isEmpty()) {
            String s = readFile(PROMPT);
            prompt = (s == null || s.trim().isEmpty()) ? DEFAULT_PROMPT : s.trim();
        }
        return prompt;
    }

    public static String recent(int n) {
        String s = readFile(SAID);
        if (s == null) {
            return "";
        }
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.length - n);
        for (int i = start; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(lines[i].trim());
        }
        return sb.toString();
    }

    public static void remember(String line) {
        try {
            String old = readFile(SAID);
            String all = (old == null ? "" : old) + line + "\n";
            String[] lines = all.split("\n");
            if (lines.length > 40) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 40; i < lines.length; i++) {
                    sb.append(lines[i]).append("\n");
                }
                all = sb.toString();
            }
            FileOutputStream out = new FileOutputStream(new File(SAID), false);
            out.write(all.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    public static String think(String state) {
        loadCfg();
        if (!enabled) {
            log("ready=false key=" + key.length());
            return null;
        }
        HttpURLConnection c = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", 1.3);
            body.put("max_tokens", 400);
            JSONObject th = new JSONObject();
            th.put("type", "disabled");
            body.put("thinking", th);
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", systemPrompt()));
            String r = recent(6);
            String user = state + (r.isEmpty() ? "" : ("\n最近说过的话，别重复：" + r));
            msgs.put(new JSONObject().put("role", "user").put("content", user));
            body.put("messages", msgs);

            c = (HttpURLConnection) new URL(base + "/chat/completions").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                log("http " + code);
                return null;
            }
            JSONObject o = new JSONObject(readStream(c.getInputStream()));
            String text = o.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content", "");
            text = clean(text);
            log((text.isEmpty() ? "empty" : "ok " + text));
            return text.isEmpty() ? null : text;
        } catch (Throwable t) {
            log("err " + t.getClass().getSimpleName() + " " + t.getMessage());
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\n", " ").replace("\r", " ").trim();
        t = t.replace("（", "").replace("）", "").replace("(", "").replace(")", "");
        t = t.replace("\"", "").replace("“", "").replace("”", "").trim();
        if (t.length() > 40) {
            t = t.substring(0, 40);
        }
        return t;
    }

    private static String readStream(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toString("UTF-8");
    }

    private static String readFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || f.length() == 0) {
                return null;
            }
            FileInputStream in = new FileInputStream(f);
            String s = readStream(in);
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void resetPrompt() {
        prompt = "";
    }

    private static void log(String text) {
        try {
            File f = new File("/sdcard/Download/Operit/pet_brain_log.txt");
            if (f.exists() && f.length() > 200 * 1024) {
                f.delete();
            }
            FileOutputStream out = new FileOutputStream(f, true);
            String ts = new java.text.SimpleDateFormat("MM-dd HH:mm:ss",
                    java.util.Locale.US).format(new java.util.Date());
            out.write((ts + "  " + text + "\n").getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Throwable ignored) {
        }
    }
}
