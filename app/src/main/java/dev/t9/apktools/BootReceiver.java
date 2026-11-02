package dev.t9.apktools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long target = Prefs.targetAt(context);
        if (target <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (target > now) {
            ResetScheduler.schedule(context, target);
            try {
                ContextCompat.startForegroundService(context,
                        new Intent(context, ResetService.class)
                                .setAction(ResetService.ACTION_START));
            } catch (Exception e) {
                LogStore.add(LogStore.WARN, "Timer restored; notification could not be restored yet");
            }
        } else {
            ResetScheduler.cancel(context);
            try {
                ContextCompat.startForegroundService(context,
                        new Intent(context, ResetService.class)
                                .setAction(ResetService.ACTION_RUN_NOW));
            } catch (Exception e) {
                LogStore.add(LogStore.WARN, "Missed timer waiting for the app to open");
            }
        }
    }
}
