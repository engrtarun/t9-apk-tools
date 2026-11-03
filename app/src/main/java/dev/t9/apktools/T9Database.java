package dev.t9.apktools;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Single SQLite database for everything that grows over time: IP readings, reset
 * runs and the activity log.
 *
 * <p>Why SQLite here and not in {@link Prefs}: preferences are a handful of
 * key/value pairs that never change size, so rewriting one small XML file is
 * cheaper than opening a database. History is append-only and needs "last N" and
 * "how many changed" queries, which is exactly what an index gives us for free.
 * Nothing is ever loaded whole: every read is a LIMIT query, the tables are
 * pruned after each write, and the file stays in the low hundreds of KB.</p>
 *
 * <p>Cost control:</p>
 * <ul>
 *   <li>{@code journal_mode=TRUNCATE} keeps the database in one file, no -wal/-shm
 *       pair on a phone with little storage.</li>
 *   <li>{@code auto_vacuum=INCREMENTAL} plus a small incremental vacuum after
 *       pruning returns freed pages instead of leaving them behind.</li>
 *   <li>Everything is written on {@link App#io()}; the UI thread never touches
 *       the disk.</li>
 * </ul>
 */
public final class T9Database extends SQLiteOpenHelper {

    public static final String KIND_AUTO = "auto";
    public static final String KIND_MANUAL = "manual";
    public static final String KIND_BEFORE = "before";
    public static final String KIND_AFTER = "after";

    private static final String NAME = "t9.db";
    private static final int VERSION = 1;

    private static final int KEEP_EVENTS = 300;
    private static final int KEEP_RUNS = 150;
    private static final int KEEP_CHECKS = 80;

    private static volatile T9Database instance;

    /** Counts writes so retention runs periodically instead of on every line. */
    private final java.util.concurrent.atomic.AtomicInteger writeCount =
            new java.util.concurrent.atomic.AtomicInteger();

    public static T9Database get(Context context) {
        if (instance == null) {
            synchronized (T9Database.class) {
                if (instance == null) {
                    instance = new T9Database(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private T9Database(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // Runs before the schema transaction, which is the only moment auto_vacuum
        // can still change for a fresh database.
        db.execSQL("PRAGMA auto_vacuum=INCREMENTAL");
        db.execSQL("PRAGMA journal_mode=TRUNCATE");
        db.execSQL("PRAGMA synchronous=NORMAL");
        db.execSQL("PRAGMA temp_store=MEMORY");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ip_check ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "ip TEXT NOT NULL,"
                + "kind TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_ip_check_at ON ip_check(at DESC)");

        db.execSQL("CREATE TABLE run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "ok INTEGER NOT NULL,"
                + "changed INTEGER NOT NULL,"
                + "before_ip TEXT,"
                + "after_ip TEXT,"
                + "engine TEXT,"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "message TEXT)");
        db.execSQL("CREATE INDEX idx_run_at ON run(at DESC)");

        db.execSQL("CREATE TABLE event ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "level TEXT NOT NULL,"
                + "message TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_event_at ON event(at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first schema. Future migrations go here; history is
        // disposable, so a drop is acceptable when a column changes shape.
    }

    /* ---------- ip_check ---------- */

    public static final class IpRow {
        public final long at;
        public final String ip;
        public final String kind;

        IpRow(long at, String ip, String kind) {
            this.at = at;
            this.ip = ip;
            this.kind = kind;
        }
    }

    public void insertIpCheck(long at, String ip, String kind) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues(3);
        values.put("at", at);
        values.put("ip", ip);
        values.put("kind", kind == null ? KIND_MANUAL : kind);
        getWritableDatabase().insert("ip_check", null, values);
        prune();
    }

    public List<IpRow> recentIpChecks(int limit) {
        List<IpRow> rows = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("ip_check",
                new String[]{"at", "ip", "kind"},
                null, null, null, null, "at DESC, id DESC", String.valueOf(limit));
        try {
            while (cursor.moveToNext()) {
                rows.add(new IpRow(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    public void clearIpChecks() {
        getWritableDatabase().delete("ip_check", null, null);
    }

    /**
     * Same address and kind inside the dedupe window: refresh the timestamp of
     * the existing row instead of stacking a duplicate on it.
     */
    public void touchIpCheck(long oldAt, String ip, String kind, long newAt) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues(1);
        values.put("at", newAt);
        getWritableDatabase().update("ip_check", values,
                "at = ? AND ip = ? AND kind = ?",
                new String[]{String.valueOf(oldAt), ip,
                        kind == null ? KIND_MANUAL : kind});
    }

    /* ---------- run ---------- */

    public static final class RunRow {
        public final long at;
        public final boolean ok;
        public final boolean changed;
        public final String beforeIp;
        public final String afterIp;
        public final String engine;
        public final long durationMs;
        public final String message;

        RunRow(long at, boolean ok, boolean changed, String beforeIp, String afterIp,
               String engine, long durationMs, String message) {
            this.at = at;
            this.ok = ok;
            this.changed = changed;
            this.beforeIp = beforeIp;
            this.afterIp = afterIp;
            this.engine = engine;
            this.durationMs = durationMs;
            this.message = message;
        }
    }

    public static final class Stats {
        public final int total;
        public final int changed;
        public final int failed;

        Stats(int total, int changed, int failed) {
            this.total = total;
            this.changed = changed;
            this.failed = failed;
        }
    }

    public void insertRun(long at, boolean ok, boolean changed, String beforeIp,
                          String afterIp, String engine, long durationMs, String message) {
        ContentValues values = new ContentValues(8);
        values.put("at", at);
        values.put("ok", ok ? 1 : 0);
        values.put("changed", changed ? 1 : 0);
        values.put("before_ip", beforeIp);
        values.put("after_ip", afterIp);
        values.put("engine", engine);
        values.put("duration_ms", Math.max(0L, durationMs));
        values.put("message", message);
        getWritableDatabase().insert("run", null, values);
        prune();
    }

    public List<RunRow> recentRuns(int limit) {
        return runs(null, limit);
    }

    /**
     * @param filter null for everything, "changed", "same" or "failed"
     */
    public List<RunRow> runs(String filter, int limit) {
        String where = null;
        if ("changed".equals(filter)) {
            where = "changed=1";
        } else if ("same".equals(filter)) {
            where = "changed=0 AND ok=1";
        } else if ("failed".equals(filter)) {
            where = "ok=0";
        }
        List<RunRow> rows = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("run",
                new String[]{"at", "ok", "changed", "before_ip", "after_ip",
                        "engine", "duration_ms", "message"},
                where, null, null, null, "at DESC, id DESC", String.valueOf(limit));
        try {
            while (cursor.moveToNext()) {
                rows.add(new RunRow(cursor.getLong(0), cursor.getInt(1) == 1,
                        cursor.getInt(2) == 1, cursor.getString(3), cursor.getString(4),
                        cursor.getString(5), cursor.getLong(6), cursor.getString(7)));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    /** One aggregate query instead of loading rows into memory. */
    public Stats stats() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*),"
                        + " COALESCE(SUM(changed),0),"
                        + " COALESCE(SUM(CASE WHEN ok=0 THEN 1 ELSE 0 END),0)"
                        + " FROM run", null);
        try {
            if (cursor.moveToFirst()) {
                return new Stats(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2));
            }
        } finally {
            cursor.close();
        }
        return new Stats(0, 0, 0);
    }

    public void clearRuns() {
        getWritableDatabase().delete("run", null, null);
    }

    /** Best local three-hour block by change rate, or null when there is not enough data. */
    public static final class Window {
        public final int startHour;
        public final int runs;
        public final int changed;

        Window(int startHour, int runs, int changed) {
            this.startHour = startHour;
            this.runs = runs;
            this.changed = changed;
        }
    }

    public Window bestWindow() {
        /* SQLite applies the device timezone per row ('localtime'), so runs from
           the other DST period keep their real local hour instead of shifting by
           whatever the current offset happens to be. */
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT (CAST(strftime('%H', at/1000.0, 'unixepoch', 'localtime')"
                        + " AS INTEGER)/3)%8 AS b, COUNT(*), SUM(changed)"
                        + " FROM run WHERE ok=1"
                        + " GROUP BY b HAVING COUNT(*) >= 2"
                        + " ORDER BY (SUM(changed)*1.0/COUNT(*)) DESC, COUNT(*) DESC LIMIT 1", null);
        try {
            if (cursor.moveToFirst()) {
                return new Window(cursor.getInt(0) * 3, cursor.getInt(1), cursor.getInt(2));
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    /** How many of the newest runs ended on the exact same address. */
    public int sameAddressStreak() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT after_ip FROM run WHERE ok=1 AND after_ip IS NOT NULL"
                        + " ORDER BY at DESC LIMIT 10", null);
        int streak = 0;
        String first = null;
        try {
            while (cursor.moveToNext()) {
                String ip = cursor.getString(0);
                if (first == null) {
                    first = ip;
                    streak = 1;
                } else if (first.equals(ip)) {
                    streak++;
                } else {
                    break;
                }
            }
        } finally {
            cursor.close();
        }
        return streak;
    }

    /** Log lines around a single run, used by the expandable trace in History. */
    public List<LogStore.Item> eventsBetween(long from, long to, int limit) {
        List<LogStore.Item> rows = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("event",
                new String[]{"at", "level", "message"},
                "at BETWEEN ? AND ?", new String[]{String.valueOf(from), String.valueOf(to)},
                null, null, "at ASC", String.valueOf(limit));
        try {
            while (cursor.moveToNext()) {
                rows.add(new LogStore.Item(cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2)));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    /** Database file size in bytes, for the diagnostics line in Setup. */
    public long sizeBytes() {
        long pages = 0L;
        long pageSize = 0L;
        Cursor cursor = getReadableDatabase().rawQuery("PRAGMA page_count", null);
        try {
            if (cursor.moveToFirst()) {
                pages = cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        cursor = getReadableDatabase().rawQuery("PRAGMA page_size", null);
        try {
            if (cursor.moveToFirst()) {
                pageSize = cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        return pages * pageSize;
    }

    /* ---------- event ---------- */

    public void insertEvent(long at, String level, String message) {
        if (message == null) {
            return;
        }
        ContentValues values = new ContentValues(3);
        values.put("at", at);
        values.put("level", level == null ? LogStore.INFO : level);
        values.put("message", message);
        getWritableDatabase().insert("event", null, values);
        prune();
    }

    public List<LogStore.Item> recentEvents(int limit) {
        List<LogStore.Item> rows = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("event",
                new String[]{"at", "level", "message"},
                null, null, null, null, "at DESC, id DESC", String.valueOf(limit));
        try {
            while (cursor.moveToNext()) {
                rows.add(new LogStore.Item(cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2)));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    public void clearEvents() {
        getWritableDatabase().delete("event", null, null);
    }

    /* ---------- maintenance ---------- */

    private void prune() {
        /* Retention is exact enough without running five write transactions on
           every single log line. Prune every 32 writes, vacuum every 128. */
        int writes = writeCount.incrementAndGet();
        if ((writes & 31) != 0) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM ip_check WHERE id < (SELECT MAX(id) - " + (KEEP_CHECKS - 1)
                    + " FROM ip_check)");
            db.execSQL("DELETE FROM run WHERE id < (SELECT MAX(id) - " + (KEEP_RUNS - 1)
                    + " FROM run)");
            db.execSQL("DELETE FROM event WHERE id < (SELECT MAX(id) - " + (KEEP_EVENTS - 1)
                    + " FROM event)");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if ((writes & 127) == 0) {
            try {
                db.execSQL("PRAGMA incremental_vacuum(16)");
            } catch (Exception ignored) {
            }
        }
    }
}
