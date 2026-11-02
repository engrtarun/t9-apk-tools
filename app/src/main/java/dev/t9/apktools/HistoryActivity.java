package dev.t9.apktools;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Every reset this phone has run, read straight from SQLite.
 *
 * <p>This is the screen that answers "what was my IP before, what did the reset
 * actually produce, and did it work last time". Stats come from one aggregate
 * query, the list is a LIMIT query, and each row can expand into the log lines
 * that were written during that run.</p>
 */
public class HistoryActivity extends AppCompatActivity {

    private static final int PAGE = 40;

    private TextView tvHistoryMeta;
    private TextView tvStatRuns;
    private TextView tvStatChanged;
    private TextView tvStatFailed;
    private TextView tvInsight;
    private LinearLayout runContainer;
    private TextView[] filters;

    private String filter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        tvHistoryMeta = findViewById(R.id.tvHistoryMeta);
        tvStatRuns = findViewById(R.id.tvStatRuns);
        tvStatChanged = findViewById(R.id.tvStatChanged);
        tvStatFailed = findViewById(R.id.tvStatFailed);
        tvInsight = findViewById(R.id.tvInsight);
        runContainer = findViewById(R.id.runContainer);
        filters = new TextView[]{
                findViewById(R.id.filterAll),
                findViewById(R.id.filterChanged),
                findViewById(R.id.filterSame),
                findViewById(R.id.filterFailed)
        };

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String[] values = {null, "changed", "same", "failed"};
        for (int i = 0; i < filters.length; i++) {
            final String value = values[i];
            filters[i].setOnClickListener(v -> {
                filter = value;
                syncFilters();
                load();
            });
        }

        findViewById(R.id.btnShare).setOnClickListener(v -> share());
        findViewById(R.id.btnClearRuns).setOnClickListener(v -> confirmClear());

        syncFilters();
        load();
    }

    private void syncFilters() {
        for (TextView chip : filters) {
            chip.setSelected(false);
        }
        int index = 0;
        if ("changed".equals(filter)) {
            index = 1;
        } else if ("same".equals(filter)) {
            index = 2;
        } else if ("failed".equals(filter)) {
            index = 3;
        }
        filters[index].setSelected(true);
    }

    private void load() {
        final String active = filter;
        App.runIo(() -> {
            T9Database db = T9Database.get(this);
            T9Database.Stats stats = db.stats();
            T9Database.Window window = db.bestWindow();
            int streak = db.sameAddressStreak();
            long size = db.sizeBytes();
            List<T9Database.RunRow> rows = db.runs(active, PAGE);
            runOnUiThread(() -> bind(stats, window, streak, size, rows));
        });
    }

    private void bind(T9Database.Stats stats, T9Database.Window window, int streak,
                      long size, List<T9Database.RunRow> rows) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        tvStatRuns.setText(String.valueOf(stats.total));
        tvStatChanged.setText(String.valueOf(stats.changed));
        tvStatFailed.setText(String.valueOf(stats.failed));
        tvHistoryMeta.setText(stats.total == 0
                ? "NOTHING RECORDED YET"
                : stats.total + " RUNS · " + formatSize(size) + " ON THIS PHONE");
        tvInsight.setText(insight(stats, window, streak));

        runContainer.removeAllViews();
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(stats.total == 0
                    ? "No resets yet. Arm the timer or press RESET NOW on the main screen."
                    : "No runs match this filter.");
            empty.setTextSize(12);
            empty.setLineSpacing(0f, 1.3f);
            empty.setTextColor(ContextCompat.getColor(this, R.color.muted));
            empty.setPadding(0, dp(16), 0, dp(16));
            runContainer.addView(empty);
            return;
        }
        for (T9Database.RunRow row : rows) {
            runContainer.addView(runRow(row));
        }
    }

    private String insight(T9Database.Stats stats, T9Database.Window window, int streak) {
        if (stats.total == 0) {
            return "Every reset will be listed here with the address before and after.";
        }
        if (streak >= 3) {
            return "The last " + streak + " resets returned the same address. The carrier is "
                    + "holding the session — wait a few minutes or toggle mobile data.";
        }
        if (window != null && window.runs >= 2) {
            return "Best window so far: around " + String.format(java.util.Locale.US, "%02d:00", window.startHour)
                    + " — " + window.changed + " of " + window.runs + " resets produced a new address.";
        }
        if (stats.changed == 0) {
            return "No new address yet. CGNAT can reuse one for a while; try a longer wait.";
        }
        int rate = Math.round(stats.changed * 100f / Math.max(1, stats.total));
        return rate + "% of resets produced a new address. Tap a row to see its log.";
    }

    private View runRow(T9Database.RunRow row) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_compare);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        card.setLayoutParams(cardParams);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView time = new TextView(this);
        time.setText(TimeUtil.clockTime(row.at));
        time.setTextSize(10);
        time.setTypeface(android.graphics.Typeface.MONOSPACE);
        time.setTextColor(ContextCompat.getColor(this, R.color.muted_dim));
        head.addView(time);

        TextView engine = new TextView(this);
        engine.setText(row.engine == null || row.engine.isEmpty()
                ? "AUTO" : row.engine.toUpperCase(java.util.Locale.US));
        engine.setTextSize(9);
        engine.setLetterSpacing(0.08f);
        engine.setTextColor(ContextCompat.getColor(this, R.color.muted_dim));
        LinearLayout.LayoutParams engineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        engineParams.leftMargin = dp(10);
        engineParams.weight = 1f;
        head.addView(engine, engineParams);

        TextView verdict = new TextView(this);
        verdict.setTextSize(9);
        verdict.setLetterSpacing(0.08f);
        verdict.setAllCaps(true);
        verdict.setPadding(dp(8), dp(3), dp(8), dp(3));
        if (!row.ok) {
            verdict.setText("FAILED");
            verdict.setTextColor(ContextCompat.getColor(this, R.color.warn));
            verdict.setBackgroundResource(R.drawable.bg_pill_warn);
        } else if (row.changed) {
            verdict.setText("NEW IP");
            verdict.setTextColor(ContextCompat.getColor(this, R.color.ok));
            verdict.setBackgroundResource(R.drawable.bg_pill_ok);
        } else {
            verdict.setText("SAME");
            verdict.setTextColor(ContextCompat.getColor(this, R.color.muted));
            verdict.setBackgroundResource(R.drawable.bg_pill);
        }
        head.addView(verdict);
        card.addView(head);

        TextView line = new TextView(this);
        line.setText(addressLine(row));
        line.setTextSize(12);
        line.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        line.setTextColor(ContextCompat.getColor(this, R.color.text));
        line.setPadding(0, dp(5), 0, 0);
        card.addView(line);

        TextView detail = new TextView(this);
        detail.setText(detailLine(row));
        detail.setTextSize(10);
        detail.setLineSpacing(0f, 1.25f);
        detail.setTextColor(ContextCompat.getColor(this, R.color.muted));
        detail.setPadding(0, dp(3), 0, 0);
        card.addView(detail);

        LinearLayout trace = new LinearLayout(this);
        trace.setOrientation(LinearLayout.VERTICAL);
        trace.setVisibility(View.GONE);
        trace.setPadding(0, dp(8), 0, 0);
        card.addView(trace);

        card.setOnClickListener(v -> {
            if (trace.getVisibility() == View.VISIBLE) {
                trace.setVisibility(View.GONE);
                return;
            }
            trace.setVisibility(View.VISIBLE);
            if (trace.getChildCount() > 0) {
                return;
            }
            loadTrace(row, trace);
        });

        return card;
    }

    private void loadTrace(T9Database.RunRow row, LinearLayout trace) {
        App.runIo(() -> {
            List<LogStore.Item> items = T9Database.get(this)
                    .eventsBetween(row.at - 90_000L, row.at + 180_000L, 14);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                trace.removeAllViews();
                if (items.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("No log lines recorded for this run.");
                    empty.setTextSize(10);
                    empty.setTextColor(ContextCompat.getColor(this, R.color.muted_dim));
                    trace.addView(empty);
                    return;
                }
                for (LogStore.Item item : items) {
                    TextView line = new TextView(this);
                    line.setText(TimeUtil.logTime(item.at) + "  " + item.message);
                    line.setTextSize(10);
                    line.setTypeface(android.graphics.Typeface.MONOSPACE);
                    line.setLineSpacing(0f, 1.25f);
                    line.setTextColor(ContextCompat.getColor(this, colorFor(item.level)));
                    line.setPadding(0, dp(2), 0, dp(2));
                    trace.addView(line);
                }
            });
        });
    }

    private int colorFor(String level) {
        if (LogStore.OK.equals(level)) {
            return R.color.ok;
        }
        if (LogStore.WARN.equals(level)) {
            return R.color.warn;
        }
        if (LogStore.ERR.equals(level)) {
            return R.color.danger;
        }
        return R.color.muted;
    }

    private String addressLine(T9Database.RunRow row) {
        if (row.beforeIp != null && row.afterIp != null) {
            if (row.beforeIp.equals(row.afterIp)) {
                return row.afterIp;
            }
            return row.beforeIp + "  →  " + row.afterIp;
        }
        if (row.afterIp != null) {
            return row.afterIp;
        }
        if (row.beforeIp != null) {
            return row.beforeIp + "  →  no reading";
        }
        return "No IP reading";
    }

    private String detailLine(T9Database.RunRow row) {
        StringBuilder builder = new StringBuilder();
        builder.append(TimeUtil.dayStamp(row.at));
        if (row.durationMs > 0L) {
            builder.append(" · ").append(Math.max(1L, row.durationMs / 1000L)).append(" s");
        }
        if (row.message != null && !row.message.isEmpty()) {
            builder.append(" · ").append(row.message);
        }
        return builder.toString();
    }

    private void share() {
        App.runIo(() -> {
            StringBuilder builder = new StringBuilder();
            T9Database db = T9Database.get(this);
            T9Database.Stats stats = db.stats();
            builder.append("T9 APK Tools — reset history\n")
                    .append(stats.total).append(" runs · ")
                    .append(stats.changed).append(" new IP · ")
                    .append(stats.failed).append(" failed\n\n");
            for (T9Database.RunRow row : db.runs(null, 60)) {
                builder.append(TimeUtil.dayStamp(row.at)).append(' ')
                        .append(TimeUtil.clockTime(row.at)).append("  ");
                if (!row.ok) {
                    builder.append("FAILED  ");
                } else if (row.changed) {
                    builder.append("NEW IP  ");
                } else {
                    builder.append("SAME    ");
                }
                builder.append(addressLine(row).replace("  →  ", " -> "));
                if (row.message != null && !row.message.isEmpty()) {
                    builder.append("  (").append(row.message).append(')');
                }
                builder.append('\n');
            }
            final String text = builder.toString();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, "T9 APK Tools — reset history");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(intent, "Share history"));
            });
        });
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear reset history?")
                .setMessage("This deletes the saved runs and log lines on this phone. "
                        + "The current IP reading and your settings stay.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    App.runIo(() -> {
                        T9Database.get(this).clearRuns();
                        LogStore.clear(this);
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                            load();
                        });
                    });
                })
                .show();
    }

    private String formatSize(long bytes) {
        if (bytes <= 0L) {
            return "0 KB";
        }
        long kb = Math.max(1L, bytes / 1024L);
        return kb < 1024L ? kb + " KB" : String.format(java.util.Locale.US, "%.1f MB", kb / 1024f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
