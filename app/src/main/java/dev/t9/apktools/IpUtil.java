package dev.t9.apktools;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class IpUtil {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String[] ENDPOINTS = {
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip"
    };

    private IpUtil() {
    }

    public static String fetch() {
        for (String endpoint : ENDPOINTS) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "T9-APK-Tools/1.0");
                connection.setRequestProperty("Accept", "text/plain");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    continue;
                }
                StringBuilder builder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && builder.length() < 128) {
                        builder.append(line.trim());
                    }
                }
                String value = builder.toString().trim();
                if (isIp(value)) {
                    return value;
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    public static void fetchAsync(Consumer<String> callback) {
        App.runIo(() -> {
            String ip = fetch();
            MAIN.post(() -> callback.accept(ip));
        });
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static boolean waitForInternet(Context context, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (isOnline(context) && !ResetEngine.isAirplaneOn(context)) {
                return true;
            }
            sleep(1000);
        }
        return isOnline(context);
    }

    /**
     * Wait for a usable network without requiring Airplane mode to be off. The
     * data engine never touches Airplane mode, so a leftover Airplane state
     * from before the run must not fail an otherwise healthy cycle.
     */
    public static boolean waitForOnline(Context context, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (isOnline(context)) {
                return true;
            }
            sleep(1000);
        }
        return isOnline(context);
    }

    private static boolean isIp(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 3 || trimmed.length() > 45) {
            return false;
        }
        if (trimmed.indexOf(':') >= 0) {
            /* IPv6: hex groups with zero-compression. Dots are not allowed, so
               a mixed-up endpoint response cannot pass as a reading. */
            return trimmed.matches("[0-9a-fA-F]{0,4}(:[0-9a-fA-F]{0,4})+")
                    && !trimmed.contains(":::");
        }
        String quad = "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
        return trimmed.matches(quad + "(\\." + quad + "){3}");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
