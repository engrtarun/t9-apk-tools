package dev.t9.apktools;

/**
 * Runs inside the Shizuku user-service process with the ADB shell identity.
 * Only the two telephony switches live here; the app process never needs shell
 * access for anything else.
 *
 * <p>The class name is handed to Shizuku as a component, so renaming it needs a
 * service version bump in {@link ShellEngine}.</p>
 */
public class ShellUserService extends IShellActions.Stub {

    /** Guards {@link #watchdog}; the watchdog re-enables data if the app dies. */
    private static final Object WATCH_LOCK = new Object();
    private static Thread watchdog;

    @Override
    public String setMobileData(boolean enable, int watchSeconds) {
        String state = enable ? "enable" : "disable";
        String result = switchData(state);
        if (enable) {
            cancelWatchdog();
        } else if (result.startsWith("ok:") && watchSeconds > 0) {
            armWatchdog(watchSeconds);
        }
        return result;
    }

    @Override
    public void destroy() {
        cancelWatchdog();
        System.exit(0);
    }

    /**
     * Turns mobile data back on after {@code seconds} unless the app got there
     * first. The app calls this only on the way down; a normal run switches data
     * back on within the hold window and cancels the thread. If the app process
     * is killed mid-hold, this is the safety net.
     */
    private static void armWatchdog(int seconds) {
        cancelWatchdog();
        final long waitMs = Math.max(5, seconds) * 1000L;
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                return;
            }
            /* Enabling data that is already enabled is harmless, so a late
               watchdog cannot fight the normal path. */
            switchData("enable");
        }, "t9-data-watchdog");
        thread.setDaemon(true);
        synchronized (WATCH_LOCK) {
            watchdog = thread;
        }
        thread.start();
    }

    private static void cancelWatchdog() {
        Thread thread;
        synchronized (WATCH_LOCK) {
            thread = watchdog;
            watchdog = null;
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static String switchData(String state) {
        int cmdPhone = run(new String[]{"/system/bin/cmd", "phone", "data", state});
        if (cmdPhone == 0) {
            return "ok:cmd-phone-" + state;
        }
        /* Android 11+ exposes "cmd phone data". Before that — and on a few OEM
           builds — the older "svc data" entry point is the fallback. Try both
           before reporting a failure, since the exact availability differs by
           build rather than by version alone. */
        int svc = run(new String[]{"/system/bin/svc", "data", state});
        if (svc == 0) {
            return "ok:svc-" + state;
        }
        return "fail:cmd=" + cmdPhone + ",svc=" + svc;
    }

    private static int run(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            java.io.InputStream stream = process.getInputStream();
            byte[] buffer = new byte[256];
            while (stream.read(buffer) != -1) {
                // Drain the pipe so the child can never block on a full buffer.
            }
            stream.close();
            if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroy();
                return -1;
            }
            return process.exitValue();
        } catch (Throwable t) {
            if (process != null) {
                process.destroy();
            }
            return -2;
        }
    }
}
