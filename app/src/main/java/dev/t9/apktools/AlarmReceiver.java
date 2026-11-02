package dev.t9.apktools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long target = Prefs.targetAt(context);
        if (target <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < target - 3000L) {
            ResetScheduler.schedule(context, target);
            return;
        }
        LogStore.add(LogStore.INFO, "Alarm fired");
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, ResetService.class)
                            .setAction(ResetService.ACTION_RUN_NOW));
        } catch (Exception e) {
            LogStore.add(LogStore.ERR, "Could not start reset service: " + e.getClass().getSimpleName());
        }
    }
}
