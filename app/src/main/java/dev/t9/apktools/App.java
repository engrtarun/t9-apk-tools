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
        io = Executors.newCachedThreadPool();
        LogStore.init(this);
        Notifications.createChannels(this);
    }

    public static App get() {
        return instance;
    }

    public static ExecutorService io() {
        return instance.io;
    }
}
