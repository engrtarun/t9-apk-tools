package dev.t9.apktools;

import android.app.Application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private static App instance;
    private ExecutorService io;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        installCrashHandler();
        io = Executors.newCachedThreadPool();
        /* None of these is allowed to decide whether the app launches. A broken
           cache costs history, not the whole process. */
        try {
            LogStore.init(this);
        } catch (Throwable error) {
            noteFailure("LogStore.init", error);
        }
        try {
            IpHistory.init(this);
        } catch (Throwable error) {
            noteFailure("IpHistory.init", error);
        }
        try {
            Notifications.createChannels(this);
        } catch (Throwable error) {
            noteFailure("Notifications.createChannels", error);
        }
    }

    /**
     * Keeps the last fatal stack trace on disk so the next launch can show it.
     * Without this a release build that dies before the first frame is a black
     * box: the user sees the app close and there is nothing to send back.
     */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                java.io.StringWriter writer = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(writer));
                java.io.FileOutputStream out = openFileOutput("last-crash.txt", MODE_PRIVATE);
                try {
                    out.write(writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } finally {
                    out.close();
                }
            } catch (Throwable ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    public static App get() {
        return instance;
    }

    public static ExecutorService io() {
        return instance.io;
    }

    /**
     * Runs a background task without letting a failure take the process down.
     * A broken database or a racing teardown should cost history, not the app.
     */
    public static void runIo(Runnable task) {
        if (instance == null || task == null) {
            return;
        }
        instance.io.execute(() -> {
            try {
                task.run();
            } catch (Throwable error) {
                noteFailure("background task", error);
            }
        });
    }

    /** Appends a non-fatal failure to the diagnostics file read on the next launch. */
    public static void noteFailure(String where, Throwable error) {
        if (instance == null || error == null) {
            return;
        }
        try {
            java.io.StringWriter writer = new java.io.StringWriter();
            writer.write("Non-fatal: ");
            writer.write(where == null ? "unknown" : where);
            writer.write('\n');
            error.printStackTrace(new java.io.PrintWriter(writer));
            writer.write('\n');
            java.io.FileOutputStream out = instance.openFileOutput("last-failure.txt", MODE_APPEND);
            try {
                out.write(writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
