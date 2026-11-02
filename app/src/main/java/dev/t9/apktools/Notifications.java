package dev.t9.apktools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class Notifications {

    public static final int ID_TIMER = 1001;
    public static final int ID_RESULT = 1002;
    public static final int ID_UNLOCK = 1003;

    private static final String CHANNEL_TIMER = "timer";
    private static final String CHANNEL_RESULT = "result";
    private static final String CHANNEL_ALERT = "alert";

    private Notifications() {
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel timer = new NotificationChannel(
                CHANNEL_TIMER,
                context.getString(R.string.channel_timer),
                NotificationManager.IMPORTANCE_LOW);
        timer.setDescription("Persistent countdown while a reset is armed.");
        timer.setShowBadge(false);
        manager.createNotificationChannel(timer);

        NotificationChannel result = new NotificationChannel(
                CHANNEL_RESULT,
                context.getString(R.string.channel_result),
                NotificationManager.IMPORTANCE_DEFAULT);
        result.setDescription("Shows whether the network reset finished.");
        manager.createNotificationChannel(result);

        NotificationChannel alert = new NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert),
                NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("Asks you to unlock the phone so the reset can finish.");
        manager.createNotificationChannel(alert);
    }

    public static Notification timer(Context context, long remainingMs, long totalMs) {
        long total = Math.max(1000L, totalMs);
        int progress = (int) Math.max(0L, Math.min(1000L, remainingMs * 1000L / total));
        return base(context, CHANNEL_TIMER)
                .setContentTitle("IP reset in " + TimeUtil.countdown(remainingMs))
                .setContentText("T9 APK Tools • tap to open")
                .setProgress(1000, progress, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(R.drawable.ic_close, "Cancel", cancelIntent(context))
                .build();
    }

    public static Notification running(Context context, String status) {
        return base(context, CHANNEL_TIMER)
                .setContentTitle("Switching network…")
                .setContentText(status == null ? "Airplane mode on, then back off" : status)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    public static void showResult(Context context, boolean success, boolean changed,
                                  String beforeIp, String afterIp, String message) {
        String title = success
                ? (changed ? "IP changed" : "Network reset complete")
                : "Reset needs attention";
        StringBuilder body = new StringBuilder(message == null ? "" : message);
        if (beforeIp != null && afterIp != null) {
            body.append('\n').append(beforeIp).append("  →  ").append(afterIp);
        }
        Notification notification = base(context, CHANNEL_RESULT)
                .setContentTitle(title)
                .setContentText(body.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        notify(context, ID_RESULT, notification);
    }

    public static void showUnlock(Context context) {
        Notification notification = base(context, CHANNEL_ALERT)
                .setContentTitle("Unlock to finish the reset")
                .setContentText("The Accessibility engine cannot tap a locked screen.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "Unlock your phone. T9 APK Tools will switch Airplane mode and finish the reset automatically."))
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build();
        notify(context, ID_UNLOCK, notification);
    }

    public static void cancelUnlock(Context context) {
        NotificationManagerCompat.from(context).cancel(ID_UNLOCK);
    }

    private static NotificationCompat.Builder base(Context context, String channel) {
        return new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(200, 255, 46))
                .setContentIntent(openIntent(context))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
    }

    private static PendingIntent openIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 10, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent cancelIntent(Context context) {
        Intent intent = new Intent(context, ResetService.class)
                .setAction(ResetService.ACTION_CANCEL);
        return PendingIntent.getService(context, 11, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void notify(Context context, int id, Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification);
        } catch (SecurityException ignored) {
        }
    }
}
