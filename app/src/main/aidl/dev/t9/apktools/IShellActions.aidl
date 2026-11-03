package dev.t9.apktools;

interface IShellActions {
    /**
     * Switches mobile data and reports "ok:…" or "fail:…".
     *
     * <p>When {@code enable} is false and {@code watchSeconds} is positive, the
     * service arms a watchdog inside its own process: if the app is killed
     * before it switches data back on, the watchdog does it after that many
     * seconds. Enabling data cancels the watchdog.</p>
     */
    String setMobileData(boolean enable, int watchSeconds);

    /** Stops the user-service process; called after each run. */
    void destroy();
}
