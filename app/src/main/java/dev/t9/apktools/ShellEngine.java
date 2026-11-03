package dev.t9.apktools;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;
import rikka.sui.Sui;

/**
 * The no-Airplane engine. When Shizuku (or Sui) is connected, the app binds a
 * tiny user service that runs with the ADB shell identity and switches mobile
 * data off and on. That tears the data context down and re-registers it, so the
 * carrier can hand out a fresh mapping — without ever switching Airplane mode
 * on. Wi-Fi, the hotspot, and every other radio stay where they were.
 *
 * <p>Shizuku is an optional, documented helper app: install it once, start it,
 * grant this app access in Setup. No root and no shell exploit.</p>
 */
public final class ShellEngine {

    private static final Object LOCK = new Object();
    private static final long BIND_TIMEOUT_MS = 15_000L;

    private static IShellActions remote;
    private static CountDownLatch latch;
    private static Shizuku.UserServiceArgs serviceArgs;

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (LOCK) {
                remote = IShellActions.Stub.asInterface(binder);
                CountDownLatch pending = latch;
                if (pending != null) {
                    pending.countDown();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (LOCK) {
                remote = null;
            }
        }
    };

    private ShellEngine() {
    }

    /** True when Shizuku/Sui is installed and its binder is alive right now. */
    public static boolean binderAlive() {
        try {
            if (Sui.isSui()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when the binder is alive AND this app holds the Shizuku grant. */
    public static boolean granted() {
        try {
            if (Shizuku.isPreV11()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Everything the engine needs, in one check. */
    public static boolean available() {
        return binderAlive() && granted();
    }

    /** Short, human summary for the Setup pill. Never called on a hot path. */
    public static String statusLabel() {
        if (!binderAlive()) {
            return "NOT CONNECTED";
        }
        return granted() ? "READY" : "GRANT NEEDED";
    }

    /**
     * Binds the shell user service, switches mobile data off, holds long enough
     * for the data context to release, then switches it back on. Returns true
     * only when both switches were accepted.
     */
    public static boolean dataReset(Context context, ResetEngine.Listener listener, int holdSec) {
        IShellActions service = connect(context);
        if (service == null) {
            listener.log(LogStore.ERR, "Shizuku did not start the shell service. "
                    + "Open Shizuku, confirm it is running, then try again.");
            return false;
        }
        boolean success = false;
        boolean everDisabled = false;
        try {
            /* watchSeconds gives the app time to finish the normal path before
               the in-service watchdog re-enables data on its own. */
            String off = service.setMobileData(false, holdSec + 30);
            if (off == null || !off.startsWith("ok:")) {
                listener.log(LogStore.ERR, "Mobile data would not switch off (" + off + ")");
                return false;
            }
            everDisabled = true;
            listener.log(LogStore.INFO, "Mobile data off (" + describe(off)
                    + ") — Airplane mode untouched");
            sleep(holdSec * 1000L);
            listener.log(LogStore.INFO, "Data hold finished — switching mobile data back on");
            String on = service.setMobileData(true, 0);
            if (on == null || !on.startsWith("ok:")) {
                listener.log(LogStore.ERR, "Mobile data did not come back (" + on + ")");
                return false;
            }
            listener.log(LogStore.OK, "Mobile data is reconnecting (" + describe(on) + ")");
            success = true;
        } catch (Throwable t) {
            listener.log(LogStore.ERR, "Shizuku shell error: "
                    + t.getClass().getSimpleName());
        } finally {
            if (everDisabled) {
                /* Last line of defence: never leave the radio with data off,
                   even if an exception skipped the normal enable. Enabling data
                   that is already on is a no-op. */
                try {
                    service.setMobileData(true, 0);
                } catch (Throwable ignored) {
                }
            }
            close();
        }
        return success;
    }

    private static IShellActions connect(Context context) {
        synchronized (LOCK) {
            if (remote != null && remote.asBinder().pingBinder()) {
                return remote;
            }
            remote = null;
            latch = new CountDownLatch(1);
        }
        try {
            Shizuku.bindUserService(serviceArgs(context), CONNECTION);
        } catch (Throwable t) {
            listenerSafeLog("Shizuku bind failed: " + t.getClass().getSimpleName());
            return null;
        }
        CountDownLatch pending;
        synchronized (LOCK) {
            pending = latch;
        }
        try {
            if (pending != null) {
                pending.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (LOCK) {
            return remote;
        }
    }

    private static void close() {
        IShellActions service;
        synchronized (LOCK) {
            service = remote;
            remote = null;
            latch = null;
        }
        if (service != null) {
            try {
                service.destroy();
            } catch (Throwable ignored) {
            }
        }
        try {
            Shizuku.unbindUserService(serviceArgs, CONNECTION, true);
        } catch (Throwable ignored) {
        }
    }

    private static synchronized Shizuku.UserServiceArgs serviceArgs(Context context) {
        if (serviceArgs == null) {
            serviceArgs = new Shizuku.UserServiceArgs(
                    new ComponentName(context.getPackageName(),
                            ShellUserService.class.getName()))
                    .processNameSuffix("shell")
                    .debuggable(false)
                    .version(1);
        }
        return serviceArgs;
    }

    private static String describe(String status) {
        return status == null ? "unknown" : status.substring(3);
    }

    private static void listenerSafeLog(String message) {
        LogStore.add(LogStore.ERR, message);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
