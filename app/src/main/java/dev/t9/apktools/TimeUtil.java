package dev.t9.apktools;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String clockTime(long millis) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(millis));
    }

    public static String logTime(long millis) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    public static String dayStamp(long millis) {
        return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(millis));
    }

    public static String countdown(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
