package dev.t9.apktools;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class LogStore {

    public static final String INFO = "info";
    public static final String OK = "ok";
    public static final String WARN = "warn";
    public static final String ERR = "err";

    private static final String FILE = "t9_log";
    private static final String KEY = "items";
    private static final int MAX = 120;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static SharedPreferences sp;
    private static int version = 0;
    private static volatile Runnable listener;

    private LogStore() {
    }

    public static void init(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void add(String level, String message) {
        if (sp == null || message == null) {
            return;
        }
        synchronized (LOCK) {
            JSONArray current = read();
            JSONObject item = new JSONObject();
            try {
                item.put("t", System.currentTimeMillis());
                item.put("l", level == null ? INFO : level);
                item.put("m", message);
                current.put(item);
                while (current.length() > MAX) {
                    current.remove(0);
                }
                sp.edit().putString(KEY, current.toString()).apply();
                version++;
            } catch (JSONException ignored) {
            }
        }
        notifyListener();
    }

    public static JSONArray items() {
        synchronized (LOCK) {
            return read();
        }
    }

    public static int version() {
        return version;
    }

    public static void clear(Context context) {
        if (sp == null) {
            sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        }
        synchronized (LOCK) {
            sp.edit().putString(KEY, "[]").apply();
            version++;
        }
        notifyListener();
    }

    public static String asText() {
        JSONArray array = items();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            builder.append(TimeUtil.logTime(item.optLong("t")))
                    .append("  ")
                    .append(item.optString("m"))
                    .append('\n');
        }
        return builder.toString();
    }

    public static void setListener(Runnable runnable) {
        listener = runnable;
    }

    private static JSONArray read() {
        if (sp == null) {
            return new JSONArray();
        }
        try {
            return new JSONArray(sp.getString(KEY, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void notifyListener() {
        Runnable runnable = listener;
        if (runnable != null) {
            MAIN.post(runnable);
        }
    }
}
