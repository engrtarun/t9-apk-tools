package dev.t9.apktools;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

public class ResetService extends Service {

    public static final String ACTION_START = "dev.t9.apktools.START";
    public static final String ACTION_CANCEL = "dev.t9.apktools.CANCEL";
    public static final String ACTION_RUN_NOW = "dev.t9.apktools.RUN_NOW";
    public static final String EXTRA_MANUAL = "manual";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            onTick();
        }
    };

    private boolean running;
    private int tickCount;
    private PowerManager.WakeLock wakeLock;

    public static void start(Context context, String action) {
        start(context, action, true);
    }

    public static void start(Context context, String action, boolean manual) {
        try {
            Intent intent = new Intent(context, ResetService.class)
                    .setAction(action)
                    .putExtra(EXTRA_MANUAL, manual);
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            LogStore.add(LogStore.ERR, "Could not start the timer service: "
                    + e.getClass().getSimpleName());
        }
    }

    public static void cancel(Context context) {
        try {
            context.startService(new Intent(context, ResetService.class)
                    .setAction(ACTION_CANCEL));
        } catch (Exception e) {
            ResetScheduler.cancel(context);
            Prefs.setTargetAt(context, 0L);
            Prefs.setBusy(context, false);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.createChannels(this);
        Prefs.setBusy(this, false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        boolean manual = intent == null || intent.getBooleanExtra(EXTRA_MANUAL, true);

        if (ACTION_CANCEL.equals(action)) {
            startForegroundCompat(Notifications.running(this, "Cancelling timer"));
            LogStore.add(LogStore.INFO, "Timer cancelled");
            stopTimer();
            return START_NOT_STICKY;
        }

        if (ACTION_RUN_NOW.equals(action)) {
            startForegroundCompat(Notifications.running(this, "Starting reset"));
            runReset(manual);
            return START_STICKY;
        }

        long target = Prefs.targetAt(this);
        if (target <= 0L) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) {
            startForegroundCompat(Notifications.running(this, "Reset in progress"));
            return START_STICKY;
        }
        startForegroundCompat(timerNotification());
        startTicker();
        if (System.currentTimeMillis() >= target) {
            runReset(false);
        }
        return START_STICKY;
    }

    private void onTick() {
        if (running) {
            return;
        }
        long target = Prefs.targetAt(this);
        long now = System.currentTimeMillis();
        if (target <= 0L) {
            stopTimer();
            return;
        }
        long remaining = target - now;
        if (remaining <= 0L) {
            runReset(false);
            return;
        }
        tickCount++;
        if (tickCount % 5 == 0) {
            try {
                NotificationManagerCompat.from(this).notify(
                        Notifications.ID_TIMER, timerNotification());
            } catch (SecurityException ignored) {
            }
        }
        handler.postDelayed(tick, 1000);
    }

    private void runReset(boolean manual) {
        if (running) {
            return;
        }
        running = true;
        Prefs.setBusy(this, true);
        handler.removeCallbacks(tick);
        startForegroundCompat(Notifications.running(this,
                manual ? "Manual reset" : "Timer finished"));
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "T9:reset");
            wakeLock.acquire(10 * 60 * 1000L);
        }
        LogStore.add(LogStore.INFO, manual ? "Manual reset started" : "Timer finished — reset started");

        final Context app = getApplicationContext();
        final long startedAt = System.currentTimeMillis();
        App.io().execute(() -> {
            ResetEngine.Result result;
            try {
                result = ResetEngine.run(app, (level, message) -> {
                    LogStore.add(level, message);
                    handler.post(() -> {
                        if (running) {
                            startForegroundCompat(Notifications.running(
                                    ResetService.this, message));
                        }
                    });
                });
            } catch (Throwable t) {
                result = new ResetEngine.Result();
                result.message = "Unexpected error: " + t.getClass().getSimpleName();
            }
            final ResetEngine.Result finalResult = result;
            handler.post(() -> finishRun(finalResult, manual, startedAt));
        });
    }

    private void finishRun(ResetEngine.Result result, boolean manual, long startedAt) {
        running = false;
        Prefs.setBusy(this, false);
        releaseWakeLock();
        Prefs.setLastResult(this, result.success, result.ipChanged,
                result.message, result.beforeIp, result.afterIp);
        Notifications.showResult(this, result.success, result.ipChanged,
                result.beforeIp, result.afterIp, result.message);

        final long finishedAt = System.currentTimeMillis();
        final long duration = Math.max(0L, finishedAt - startedAt);
        final boolean success = result.success;
        final boolean changed = result.ipChanged;
        final String beforeIp = result.beforeIp;
        final String afterIp = result.afterIp;
        final String message = result.message;
        final String engine = result.strategy;
        final Context app = getApplicationContext();
        App.io().execute(() -> T9Database.get(app)
                .insertRun(finishedAt, success, changed, beforeIp, afterIp,
                        engine, duration, message));

        long now = System.currentTimeMillis();
        long existing = Prefs.targetAt(this);
        Prefs.setTargetAt(this, 0L);
        long next = 0L;
        if (manual) {
            if (existing > now) {
                next = existing;
            } else if (Prefs.repeat(this)) {
                Prefs.setCycleMin(this, Prefs.durationMin(this));
                next = now + Prefs.durationMin(this) * 60_000L;
            }
        } else if (Prefs.repeat(this)) {
            Prefs.setCycleMin(this, Prefs.durationMin(this));
            next = now + Prefs.durationMin(this) * 60_000L;
        }

        if (next > now) {
            Prefs.setTargetAt(this, next);
            ResetScheduler.schedule(this, next);
            startForegroundCompat(timerNotification());
            startTicker();
        } else {
            stopTimer();
        }
    }

    private void startTicker() {
        handler.removeCallbacks(tick);
        tickCount = 0;
        handler.postDelayed(tick, 1000);
    }

    private Notification timerNotification() {
        long target = Prefs.targetAt(this);
        long cycle = Prefs.cycleMin(this) * 60_000L;
        long remaining = Math.max(0L, target - System.currentTimeMillis());
        return Notifications.timer(this, remaining, cycle);
    }

    private void startForegroundCompat(Notification notification) {
        int type = Build.VERSION.SDK_INT >= 34
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                : 0;
        try {
            ServiceCompat.startForeground(this, Notifications.ID_TIMER, notification, type);
        } catch (Exception ignored) {
        }
    }

    private void stopTimer() {
        handler.removeCallbacks(tick);
        Prefs.setTargetAt(this, 0L);
        Prefs.setBusy(this, false);
        ResetScheduler.cancel(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        releaseWakeLock();
        if (Prefs.targetAt(this) > System.currentTimeMillis()) {
            ResetScheduler.schedule(this, Prefs.targetAt(this));
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (Prefs.targetAt(this) > System.currentTimeMillis()) {
            ResetScheduler.schedule(this, Prefs.targetAt(this));
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
