package dev.t9.apktools;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Public-IP ledger, stored in SQLite and mirrored into a 12-entry cache so the
 * card can render without touching the disk.
 *
 * <p>Every reading keeps the moment it was taken, so the card can show an honest
 * before/after pair for the last reset instead of one unexplained value. The
 * database keeps 80 rows with an index on {@code at}; the UI only ever shows 8.</p>
 */
public final class IpHistory {

    /** Read by the app when it opens or when the card is refreshed. */
    public static final String KIND_AUTO = T9Database.KIND_AUTO;
    /** Read because the user pressed refresh. */
    public static final String KIND_MANUAL = T9Database.KIND_MANUAL;
    /** Read right before the Airplane toggle. */
    public static final String KIND_BEFORE = T9Database.KIND_BEFORE;
    /** Read right after the network came back. */
    public static final String KIND_AFTER = T9Database.KIND_AFTER;

    private static final int CACHE_SIZE = 12;
    private static final int DB_LIMIT = 40;
    private static final long SAME_KIND_WINDOW_MS = 10 * 60 * 1000L;

    private static final Object LOCK = new Object();
    private static final List<Entry> CACHE = new ArrayList<>(CACHE_SIZE);
    private static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static boolean loaded;
    private static Context appContext;
    private static volatile Runnable listener;

    public static final class Entry {
        public final long at;
        public final String ip;
        public final String kind;

        public Entry(long at, String ip, String kind) {
            this.at = at;
            this.ip = ip;
            this.kind = kind;
        }

        public boolean isResetKind() {
            return KIND_BEFORE.equals(kind) || KIND_AFTER.equals(kind);
        }
    }

    private IpHistory() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        App.io().execute(() -> {
            List<T9Database.IpRow> rows = T9Database.get(appContext).recentIpChecks(DB_LIMIT);
            synchronized (LOCK) {
                CACHE.clear();
                for (T9Database.IpRow row : rows) {
                    CACHE.add(new Entry(row.at, row.ip, row.kind));
                }
                loaded = true;
            }
            notifyListener();
        });
    }

    public static void setListener(Runnable runnable) {
        listener = runnable;
    }

    private static void notifyListener() {
        Runnable runnable = listener;
        if (runnable != null) {
            MAIN.post(runnable);
        }
    }

    /** Newest first. Returns a copy, never the live cache. */
    public static List<Entry> all(Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        synchronized (LOCK) {
            if (!loaded) {
                loaded = true;
                App.io().execute(() -> {
                    List<T9Database.IpRow> rows =
                            T9Database.get(appContext).recentIpChecks(DB_LIMIT);
                    synchronized (LOCK) {
                        if (!CACHE.isEmpty()) {
                            return;
                        }
                        for (T9Database.IpRow row : rows) {
                            CACHE.add(new Entry(row.at, row.ip, row.kind));
                        }
                    }
                    notifyListener();
                });
            }
            return new ArrayList<>(CACHE);
        }
    }

    public static void add(Context context, String ip, String kind) {
        add(context, ip, kind, System.currentTimeMillis());
    }

    public static void add(Context context, String ip, String kind, long at) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        final Context app = appContext;
        synchronized (LOCK) {
            if (!CACHE.isEmpty()) {
                Entry head = CACHE.get(0);
                boolean sameAddress = head.ip.equals(ip);
                boolean sameKind = head.kind.equals(kind);
                boolean quickRepeat = Math.abs(at - head.at) < SAME_KIND_WINDOW_MS;
                if (sameAddress && sameKind && quickRepeat) {
                    CACHE.set(0, new Entry(at, ip, kind));
                    // Same reading repeated: move the timestamp, no new row.
                    return;
                }
            }
            CACHE.add(0, new Entry(at, ip, kind));
            while (CACHE.size() > CACHE_SIZE) {
                CACHE.remove(CACHE.size() - 1);
            }
        }
        final String finalKind = kind;
        final long finalAt = at;
        App.io().execute(() -> T9Database.get(app).insertIpCheck(finalAt, ip, finalKind));
    }

    public static Entry newest(Context context) {
        synchronized (LOCK) {
            return CACHE.isEmpty() ? null : CACHE.get(0);
        }
    }

    public static Entry newestOf(Context context, String kind) {
        synchronized (LOCK) {
            for (Entry entry : CACHE) {
                if (entry.kind.equals(kind)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * The "before" reading that belongs to the newest "after" reading. The cache is
     * newest first, so the partner is the first older entry with kind "before".
     */
    public static Entry partnerBefore(Context context) {
        List<Entry> entries = all(context);
        int afterIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (KIND_AFTER.equals(entries.get(i).kind)) {
                afterIndex = i;
                break;
            }
        }
        if (afterIndex < 0) {
            return null;
        }
        for (int i = afterIndex + 1; i < entries.size(); i++) {
            if (KIND_BEFORE.equals(entries.get(i).kind)) {
                return entries.get(i);
            }
        }
        return null;
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            CACHE.clear();
        }
        final Context app = context.getApplicationContext();
        App.io().execute(() -> T9Database.get(app).clearIpChecks());
    }

    public static List<Entry> snapshot() {
        synchronized (LOCK) {
            return Collections.unmodifiableList(new ArrayList<>(CACHE));
        }
    }
}
