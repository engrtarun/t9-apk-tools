package dev.t9.apktools;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String FILE = "t9_prefs";

    public static final String STRATEGY_AUTO = "auto";
    public static final String STRATEGY_DIRECT = "direct";
    public static final String STRATEGY_SETTINGS = "settings";
    public static final String STRATEGY_QS = "qs";

    private Prefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int durationMin(Context context) {
        return clamp(sp(context).getInt("duration", 15), 1, 180);
    }

    public static void setDurationMin(Context context, int minutes) {
        sp(context).edit().putInt("duration", clamp(minutes, 1, 180)).apply();
    }

    public static int cycleMin(Context context) {
        return clamp(sp(context).getInt("cycle_duration", durationMin(context)), 1, 180);
    }

    public static void setCycleMin(Context context, int minutes) {
        sp(context).edit().putInt("cycle_duration", clamp(minutes, 1, 180)).apply();
    }

    public static boolean repeat(Context context) {
        return sp(context).getBoolean("repeat", false);
    }

    public static void setRepeat(Context context, boolean repeat) {
        sp(context).edit().putBoolean("repeat", repeat).apply();
    }

    public static long targetAt(Context context) {
        return sp(context).getLong("target_at", 0L);
    }

    public static void setTargetAt(Context context, long targetAt) {
        sp(context).edit().putLong("target_at", Math.max(0L, targetAt)).apply();
    }

    public static boolean busy(Context context) {
        return sp(context).getBoolean("busy", false);
    }

    public static void setBusy(Context context, boolean busy) {
        sp(context).edit().putBoolean("busy", busy).apply();
    }

    public static String strategy(Context context) {
        String value = sp(context).getString("strategy", STRATEGY_AUTO);
        if (STRATEGY_DIRECT.equals(value) || STRATEGY_SETTINGS.equals(value)
                || STRATEGY_QS.equals(value) || STRATEGY_AUTO.equals(value)) {
            return value;
        }
        return STRATEGY_AUTO;
    }

    public static void setStrategy(Context context, String strategy) {
        sp(context).edit().putString("strategy", strategy).apply();
    }

    public static int offDelaySec(Context context) {
        return clamp(sp(context).getInt("off_delay", 3), 2, 12);
    }

    public static void setOffDelaySec(Context context, int seconds) {
        sp(context).edit().putInt("off_delay", clamp(seconds, 2, 12)).apply();
    }

    public static boolean wakeScreen(Context context) {
        return sp(context).getBoolean("wake", true);
    }

    public static void setWakeScreen(Context context, boolean wake) {
        sp(context).edit().putBoolean("wake", wake).apply();
    }

    public static boolean verifyIp(Context context) {
        return sp(context).getBoolean("verify_ip", true);
    }

    public static void setVerifyIp(Context context, boolean verify) {
        sp(context).edit().putBoolean("verify_ip", verify).apply();
    }

    public static boolean restoreHotspot(Context context) {
        return sp(context).getBoolean("restore_hotspot", false);
    }

    public static void setRestoreHotspot(Context context, boolean restore) {
        sp(context).edit().putBoolean("restore_hotspot", restore).apply();
    }

    public static float qsXPercent(Context context) {
        return sp(context).getFloat("qs_x", 12.5f);
    }

    public static float qsYPercent(Context context) {
        return sp(context).getFloat("qs_y", 20f);
    }

    public static void setQsXPercent(Context context, float value) {
        sp(context).edit().putFloat("qs_x", clamp(value, 1f, 99f)).apply();
    }

    public static void setQsYPercent(Context context, float value) {
        sp(context).edit().putFloat("qs_y", clamp(value, 1f, 99f)).apply();
    }

    public static String lastIp(Context context) {
        return sp(context).getString("last_ip", null);
    }

    public static void setLastIp(Context context, String ip) {
        sp(context).edit().putString("last_ip", ip).apply();
    }

    public static String prevIp(Context context) {
        return sp(context).getString("prev_ip", null);
    }

    public static void setPrevIp(Context context, String ip) {
        sp(context).edit().putString("prev_ip", ip).apply();
    }

    public static boolean lastChanged(Context context) {
        return sp(context).getBoolean("last_changed", false);
    }

    public static boolean lastSuccess(Context context) {
        return sp(context).getBoolean("last_success", false);
    }

    public static boolean notifAsked(Context context) {
        return sp(context).getBoolean("notif_asked", false);
    }

    public static void setNotifAsked(Context context) {
        sp(context).edit().putBoolean("notif_asked", true).apply();
    }

    public static boolean accessibilityHintShown(Context context) {
        return sp(context).getBoolean("a11y_hint_shown", false);
    }

    public static void setAccessibilityHintShown(Context context) {
        sp(context).edit().putBoolean("a11y_hint_shown", true).apply();
    }

    public static String lastMessage(Context context) {
        return sp(context).getString("last_message", null);
    }

    public static long lastRunAt(Context context) {
        return sp(context).getLong("last_run_at", 0L);
    }

    public static void setLastResult(Context context, boolean success, boolean changed,
                                     String message, String beforeIp, String afterIp) {
        SharedPreferences.Editor editor = sp(context).edit()
                .putBoolean("last_success", success)
                .putBoolean("last_changed", changed)
                .putString("last_message", message)
                .putLong("last_run_at", System.currentTimeMillis());
        if (beforeIp != null) {
            editor.putString("prev_ip", beforeIp);
        }
        if (afterIp != null) {
            editor.putString("last_ip", afterIp);
        }
        editor.apply();
    }

    public static void reset(Context context) {
        sp(context).edit().clear().apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
