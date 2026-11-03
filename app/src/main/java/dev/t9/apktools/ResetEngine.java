package dev.t9.apktools;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.provider.Settings;

public final class ResetEngine {

    public interface Listener {
        void log(String level, String message);
    }

    public static final class Result {
        public boolean success;
        public boolean ipChanged;
        public String beforeIp;
        public String afterIp;
        public String message = "Reset did not run";
        public String strategy = "auto";
    }

    private ResetEngine() {
    }

    public static Result run(Context context, Listener listener) {
        Result result = new Result();
        PowerManager.WakeLock screenLock = null;
        boolean initial = isAirplaneOn(context);
        try {
            listener.log(LogStore.INFO, "Reset cycle started");

            String strategy = Prefs.strategy(context);
            boolean direct = canWriteDirect(context);
            boolean accessibility = AccessibilityResetService.isEnabled(context);

            if (Prefs.STRATEGY_DIRECT.equals(strategy) && !direct) {
                result.message = "ADB permission is missing. Open Setup and copy the grant command.";
                listener.log(LogStore.ERR, result.message);
                return result;
            }
            /* Fail before the IP fetch when the chosen engine cannot run at all. */
            if (Prefs.STRATEGY_DATA.equals(strategy) && !ShellEngine.available()) {
                result.message = "Shizuku is not connected or not granted. Connect it in Setup.";
                listener.log(LogStore.ERR, result.message);
                return result;
            }
            if (!direct && !accessibility && !ShellEngine.available()) {
                result.message = "No engine enabled. Grant ADB access, connect Shizuku, or turn on Accessibility in Setup.";
                listener.log(LogStore.ERR, result.message);
                return result;
            }

            /* The ADB engine writes the setting directly, so it works on a locked
               phone. Accessibility has to drive the visible Settings UI, which a
               secure keyguard will always block. Decide that before wasting time. */
            boolean dataEngine = Prefs.STRATEGY_DATA.equals(strategy)
                    || (Prefs.STRATEGY_AUTO.equals(strategy) && !direct
                        && ShellEngine.available());
            boolean needsUnlockedScreen = !dataEngine
                    && (!direct
                        || Prefs.STRATEGY_SETTINGS.equals(strategy)
                        || Prefs.STRATEGY_QS.equals(strategy));

            if (needsUnlockedScreen && Prefs.wakeScreen(context)) {
                PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (power != null) {
                    screenLock = power.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                                    | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "T9:screen");
                    screenLock.acquire(4 * 60 * 1000L);
                    listener.log(LogStore.INFO, "Screen woken for automation");
                }
            }

            if (needsUnlockedScreen && isLocked(context)) {
                if (isDeviceSecure(context)) {
                    result.message = "Phone is locked with a PIN or pattern. Accessibility cannot "
                            + "unlock it — grant ADB access in Setup, or disable the lock screen "
                            + "for this run.";
                    listener.log(LogStore.ERR, result.message);
                    return result;
                }
                listener.log(LogStore.WARN,
                        "Phone locked — unlock to continue (waiting up to 90 s)");
                Notifications.showUnlock(context);
                if (!waitForUnlock(context, 90_000L)) {
                    result.message = "Phone stayed locked. Unlock it and run again.";
                    listener.log(LogStore.ERR, result.message);
                    return result;
                }
                listener.log(LogStore.OK, "Phone unlocked");
            }
            Notifications.cancelUnlock(context);

            if (Prefs.verifyIp(context)) {
                long beforeAt = System.currentTimeMillis();
                result.beforeIp = IpUtil.fetch();
                if (result.beforeIp != null) {
                    Prefs.setPrevIp(context, result.beforeIp);
                    IpHistory.add(context, result.beforeIp, IpHistory.KIND_BEFORE, beforeAt);
                    listener.log(LogStore.INFO, "Public IP before: " + result.beforeIp);
                } else {
                    listener.log(LogStore.WARN, "Could not read the public IP before reset");
                }
            }

            boolean toggled = false;
            switch (strategy) {
                case Prefs.STRATEGY_DIRECT:
                    result.strategy = "direct";
                    if (!canWriteDirect(context)) {
                        result.message = "ADB permission is missing. Open Setup and copy the grant command.";
                        listener.log(LogStore.ERR, result.message);
                        return result;
                    }
                    listener.log(LogStore.INFO, "Direct engine: writing the Airplane setting");
                    toggled = directToggle(context, Prefs.offDelaySec(context), listener);
                    break;
                case Prefs.STRATEGY_SETTINGS:
                    result.strategy = "accessibility";
                    listener.log(LogStore.INFO, "Accessibility engine: Settings UI");
                    toggled = viaSettings(context, listener);
                    break;
                case Prefs.STRATEGY_QS:
                    result.strategy = "accessibility";
                    listener.log(LogStore.INFO, "Accessibility engine: Quick Settings");
                    toggled = viaQuickSettings(context, listener);
                    break;
                case Prefs.STRATEGY_DATA:
                    result.strategy = "data";
                    if (!ShellEngine.available()) {
                        result.message = "Shizuku is not connected or not granted. Connect it in Setup.";
                        listener.log(LogStore.ERR, result.message);
                        return result;
                    }
                    listener.log(LogStore.INFO, "Data engine: Shizuku shell, Airplane mode untouched");
                    /* A data context needs a moment longer than an airplane hold
                       to release, so the saved hold time has a floor of 4 s. */
                    toggled = ShellEngine.dataReset(context, listener,
                            Math.max(4, Prefs.offDelaySec(context)));
                    if (!toggled) {
                        result.message = "Mobile data would not reset over Shizuku. "
                                + "Pick the Direct engine in Setup for the Airplane toggle.";
                        listener.log(LogStore.ERR, result.message);
                        return result;
                    }
                    break;
                default:
                    /* Auto favours the engine that never touches Airplane mode,
                       then the direct write, then accessibility. */
                    if (!direct && ShellEngine.available()) {
                        result.strategy = "data";
                        listener.log(LogStore.INFO, "Auto: no-Airplane data engine (Shizuku)");
                        toggled = ShellEngine.dataReset(context, listener,
                                Math.max(4, Prefs.offDelaySec(context)));
                        if (!toggled) {
                            result.message = "Mobile data would not reset over Shizuku. "
                                    + "Pick the Direct engine in Setup for the Airplane toggle.";
                            listener.log(LogStore.ERR, result.message);
                            return result;
                        }
                        break;
                    }
                    if (canWriteDirect(context)) {
                        result.strategy = "direct";
                        listener.log(LogStore.INFO, "Auto: direct ADB engine");
                        toggled = directToggle(context, Prefs.offDelaySec(context), listener);
                    } else if (AccessibilityResetService.isEnabled(context)) {
                        result.strategy = "accessibility";
                        listener.log(LogStore.INFO, "Auto: Accessibility engine");
                        toggled = viaQuickSettings(context, listener);
                        if (!toggled) {
                            listener.log(LogStore.WARN,
                                    "Quick Settings path failed — falling back to Settings");
                            toggled = viaSettings(context, listener);
                        }
                    } else {
                        result.message = "No engine enabled. Grant ADB access, connect Shizuku, or turn on Accessibility in Setup.";
                        listener.log(LogStore.ERR, result.message);
                        return result;
                    }
                    break;
            }

            if (!toggled) {
                if (isAirplaneOn(context) != initial) {
                    listener.log(LogStore.WARN, "Airplane state left wrong — restoring");
                    restoreAirplane(context, initial, listener);
                }
                result.message = "Could not toggle Airplane mode";
                listener.log(LogStore.ERR, result.message);
                return result;
            }

            if (result.strategy.equals("data")) {
                listener.log(LogStore.OK, "Mobile data cycled without touching Airplane mode");
            } else if (isAirplaneOn(context) != initial) {
                listener.log(LogStore.WARN, "Airplane mode is still on — restoring");
                if (!restoreAirplane(context, initial, listener)) {
                    result.message = "Airplane mode is still ON. Turn it off manually.";
                    listener.log(LogStore.ERR, result.message);
                    return result;
                }
                listener.log(LogStore.OK, "Airplane mode switched and restored");
            } else {
                listener.log(LogStore.OK, "Airplane mode switched and restored");
            }

            boolean online = result.strategy.equals("data")
                    ? IpUtil.waitForOnline(context, 60_000L)
                    : IpUtil.waitForInternet(context, 60_000L);
            if (!online) {
                result.message = "Network did not come back in 60 s. Check mobile data.";
                listener.log(LogStore.WARN, result.message);
                return result;
            }
            listener.log(LogStore.OK, "Network is back");

            if (Prefs.verifyIp(context)) {
                for (int attempt = 0; attempt < 4; attempt++) {
                    result.afterIp = IpUtil.fetch();
                    if (result.afterIp != null) {
                        break;
                    }
                    sleep(2000);
                }
                if (result.afterIp != null) {
                    if (result.beforeIp != null) {
                        result.ipChanged = !result.beforeIp.equals(result.afterIp);
                        listener.log(result.ipChanged ? LogStore.OK : LogStore.WARN,
                                "Public IP after: " + result.afterIp
                                        + (result.ipChanged ? " (changed)" : " (unchanged)"));
                    } else {
                        listener.log(LogStore.INFO, "Public IP after: " + result.afterIp);
                    }
                    Prefs.setLastIp(context, result.afterIp);
                    IpHistory.add(context, result.afterIp, IpHistory.KIND_AFTER,
                            System.currentTimeMillis());
                } else {
                    listener.log(LogStore.WARN, "Could not read the public IP after reset");
                }
            }

            if (Prefs.restoreHotspot(context)) {
                AccessibilityResetService service = awaitService(4000);
                if (service != null) {
                    listener.log(LogStore.INFO, "Checking Wi-Fi hotspot");
                    boolean ok = service.enableHotspotIfOff();
                    listener.log(ok ? LogStore.OK : LogStore.WARN,
                            ok ? "Wi-Fi hotspot is on" : "Hotspot state could not be confirmed");
                } else {
                    listener.log(LogStore.WARN, "Hotspot restore skipped: Accessibility not connected");
                }
            }

            result.success = true;
            result.message = buildMessage(result);
            listener.log(LogStore.OK, result.message);
            return result;
        } catch (Throwable t) {
            result.message = "Reset failed: " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " — " + t.getMessage());
            listener.log(LogStore.ERR, result.message);
            return result;
        } finally {
            if (screenLock != null && screenLock.isHeld()) {
                screenLock.release();
            }
            Notifications.cancelUnlock(context);
        }
    }

    public static boolean canWriteDirect(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isAirplaneOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    private static boolean viaSettings(Context context, Listener listener) {
        AccessibilityResetService service = awaitService(4000);
        if (service == null) {
            listener.log(LogStore.ERR,
                    "Accessibility is enabled but not connected. Toggle it off/on in Android Settings.");
            return false;
        }
        return service.resetViaSettings(Prefs.offDelaySec(context));
    }

    private static boolean viaQuickSettings(Context context, Listener listener) {
        AccessibilityResetService service = awaitService(4000);
        if (service == null) {
            listener.log(LogStore.ERR,
                    "Accessibility is enabled but not connected. Toggle it off/on in Android Settings.");
            return false;
        }
        return service.resetViaQuickSettings(
                Prefs.offDelaySec(context),
                Prefs.qsXPercent(context),
                Prefs.qsYPercent(context));
    }

    private static AccessibilityResetService awaitService(long timeoutMs) {
        AccessibilityResetService service = AccessibilityResetService.instance();
        if (service != null) {
            return service;
        }
        AccessibilityResetService.awaitConnected(timeoutMs);
        return AccessibilityResetService.instance();
    }

    private static boolean directToggle(Context context, int offDelaySec, Listener listener) {
        boolean initial = isAirplaneOn(context);
        boolean first = setAirplane(context, !initial);
        if (!first) {
            listener.log(LogStore.ERR, "Direct write was rejected by the system");
            return false;
        }
        listener.log(LogStore.INFO, "Airplane mode toggled on (direct)");
        sleep(offDelaySec * 1000L);
        boolean restored = setAirplane(context, initial);
        if (restored) {
            listener.log(LogStore.OK, "Airplane mode restored (direct)");
        }
        return restored && isAirplaneOn(context) == initial;
    }

    private static boolean setAirplane(Context context, boolean on) {
        try {
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, on ? 1 : 0);
            long end = System.currentTimeMillis() + 5000L;
            while (System.currentTimeMillis() < end) {
                if (isAirplaneOn(context) == on) {
                    return true;
                }
                sleep(200);
            }
            return isAirplaneOn(context) == on;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean restoreAirplane(Context context, boolean desired, Listener listener) {
        if (isAirplaneOn(context) == desired) {
            return true;
        }
        if (canWriteDirect(context)) {
            if (setAirplane(context, desired)) {
                listener.log(LogStore.OK, "Airplane mode restored directly");
                return true;
            }
        }
        AccessibilityResetService service = awaitService(3000);
        if (service != null && service.enforceAirplaneState(desired)) {
            listener.log(LogStore.OK, "Airplane mode restored via Settings");
            return true;
        }
        return isAirplaneOn(context) == desired;
    }

    private static boolean isLocked(Context context) {
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(
                Context.KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isKeyguardLocked();
    }

    /** True when the lock screen needs a PIN, pattern, password or biometric. */
    private static boolean isDeviceSecure(Context context) {
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(
                Context.KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isDeviceSecure();
    }

    private static boolean waitForUnlock(Context context, long timeoutMs) {
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(
                Context.KEYGUARD_SERVICE);
        if (keyguard == null) {
            return true;
        }
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (!keyguard.isKeyguardLocked()) {
                return true;
            }
            sleep(1000);
        }
        return !keyguard.isKeyguardLocked();
    }

    private static String buildMessage(Result result) {
        if (result.beforeIp != null && result.afterIp != null) {
            if (result.ipChanged) {
                return "IP changed: " + result.beforeIp + " → " + result.afterIp;
            }
            return "Reset done. IP stayed " + result.afterIp
                    + " — the carrier may hold the session. Retry or toggle data.";
        }
        if (result.afterIp != null) {
            return "Reset done. Public IP: " + result.afterIp;
        }
        return "Reset done. IP check unavailable.";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
