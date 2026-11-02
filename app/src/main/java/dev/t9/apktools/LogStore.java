package dev.t9.apktools;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Activity log backed by SQLite.
 *
 * <p>The old implementation rewrote a JSON array in SharedPreferences on every
 * line, which is O(n) disk work per event and grows without bound. This one keeps
 * a tiny ring buffer in memory for the UI and appends a single row per event.
 * Loading at start is one {@code LIMIT 120} query; nothing else is ever read.</p>
 */
public final class LogStore {

    public static final String INFO = "info";
    public static final String OK = "ok";
    public static final String WARN = "warn";
    public static final String ERR = "err";

    private static final int MAX = 120;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<Item> BUFFER = new ArrayDeque<>(MAX);

    private static Context appContext;
    private static volatile int version;
    private static volatile long clearCutoff;
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static volatile Runnable listener;

    private LogStore() {
    }

    public static final class Item {
        public final long at;
        public final String level;
        public final String message;

        public Item(long at, String level, String message) {
            this.at = at;
            this.level = level;
            this.message = message;
        }
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        App.io().execute(() -> {
            int generation;
            synchronized (LOCK) {
                generation = version;
            }
            List<Item> rows = T9Database.get(appContext).recentEvents(MAX);
            synchronized (LOCK) {
                if (version == generation) {
                    BUFFER.clear();
                    for (int i = rows.size() - 1; i >= 0; i--) {
                        BUFFER.addLast(rows.get(i));
                    }
                } else if (!BUFFER.isEmpty()) {
                    /* Lines were logged while the read was in flight (alarm start,
                       reset progress). Keep them and prepend only older rows. */
                    long head = BUFFER.peekFirst().at;
                    for (int i = rows.size() - 1; i >= 0; i--) {
                        Item item = rows.get(i);
                        if (item.at < head) {
                            BUFFER.addFirst(item);
                        }
                    }
                    while (BUFFER.size() > MAX) {
                        BUFFER.removeLast();
                    }
                }
                version++;
            }
            notifyListener();
        });
    }

    public static void add(String level, String message) {
        if (appContext == null || message == null) {
            return;
        }
        final Item item = new Item(System.currentTimeMillis(),
                level == null ? INFO : level, message);
        synchronized (LOCK) {
            BUFFER.addLast(item);
            while (BUFFER.size() > MAX) {
                BUFFER.removeFirst();
            }
            version++;
        }
        notifyListener();
        final Context context = appContext;
        App.io().execute(() -> {
            if (item.at < clearCutoff) {
                return;
            }
            T9Database.get(context).insertEvent(item.at, item.level, item.message);
        });
    }

    public static JSONArray items() {
        JSONArray array = new JSONArray();
        synchronized (LOCK) {
            for (Item item : BUFFER) {
                JSONObject object = new JSONObject();
                try {
                    object.put("t", item.at);
                    object.put("l", item.level);
                    object.put("m", item.message);
                    array.put(object);
                } catch (Exception ignored) {
                }
            }
        }
        return array;
    }

    public static int version() {
        return version;
    }

    public static void clear(Context context) {
        clearCutoff = System.currentTimeMillis();
        synchronized (LOCK) {
            BUFFER.clear();
            version++;
        }
        notifyListener();
        final Context app = context.getApplicationContext();
        App.io().execute(() -> T9Database.get(app).clearEvents());
    }

    public static String asText() {
        StringBuilder builder = new StringBuilder();
        synchronized (LOCK) {
            for (Item item : BUFFER) {
                builder.append(TimeUtil.logTime(item.at))
                        .append("  ")
                        .append(item.message)
                        .append('\n');
            }
        }
        return builder.toString();
    }

    public static void setListener(Runnable runnable) {
        listener = runnable;
    }

    private static void notifyListener() {
        if (listener == null) {
            return;
        }
        if (!PENDING.compareAndSet(false, true)) {
            return;
        }
        MAIN.post(() -> {
            PENDING.set(false);
            Runnable runnable = listener;
            if (runnable != null) {
                runnable.run();
            }
        });
    }
}
