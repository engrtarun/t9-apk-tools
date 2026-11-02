package dev.t9.apktools;

import android.Manifest;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String HOW_IT_WORKS =
            "https://engrtarun.github.io/t9-apk-tools/#how-it-works";
    private static final int[] PRESETS = {5, 10, 15, 20, 30};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!resumed) {
                return;
            }
            updateUi();
            handler.postDelayed(this, 1000L);
        }
    };

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    LogStore.add(LogStore.WARN,
                            "Notifications are off — the timer still runs, but you will not see its status.");
                }
            });

    private TextView tvStatePill;
    private TextView tvRingLabel;
    private TextView tvTime;
    private TextView tvRingStatus;
    private TextView tvNext;
    private TextView btnPrimary;
    private TextView btnResetNow;
    private androidx.appcompat.widget.SwitchCompat switchRepeat;
    private TextView chipAccess;
    private TextView chipDirect;
    private TextView tvIpBadge;
    private TextView tvIp;
    private TextView tvIpPrev;
    private View btnRefreshIp;
    private LinearLayout logContainer;
    private TextView btnClearLog;
    private TextView tvFooter;
    private TimerRingView ring;

    private TextView[] chips;
    private boolean resumed;
    private boolean exactDialogShown;
    private int lastRenderedLogVersion = -1;
    private long lastSyncedRunAt = -1L;
    private int lastMode = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        bindChips();
        bindActions();

        ring.setProgress(1f);
        refreshAccessChips();
        syncResultCard();
        renderLogs(true);
        updateUi();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !Prefs.notifAsked(this)) {
            Prefs.setNotifAsked(this);
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        LogStore.setListener(() -> renderLogs(false));
    }

    private void bindViews() {
        ring = findViewById(R.id.ring);
        tvStatePill = findViewById(R.id.tvStatePill);
        tvRingLabel = findViewById(R.id.tvRingLabel);
        tvTime = findViewById(R.id.tvTime);
        tvRingStatus = findViewById(R.id.tvRingStatus);
        tvNext = findViewById(R.id.tvNext);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnResetNow = findViewById(R.id.btnResetNow);
        switchRepeat = findViewById(R.id.switchRepeat);
        chipAccess = findViewById(R.id.chipAccess);
        chipDirect = findViewById(R.id.chipDirect);
        tvIpBadge = findViewById(R.id.tvIpBadge);
        tvIp = findViewById(R.id.tvIp);
        tvIpPrev = findViewById(R.id.tvIpPrev);
        btnRefreshIp = findViewById(R.id.btnRefreshIp);
        logContainer = findViewById(R.id.logContainer);
        btnClearLog = findViewById(R.id.btnClearLog);
        tvFooter = findViewById(R.id.tvFooter);
        chips = new TextView[]{
                findViewById(R.id.chip5), findViewById(R.id.chip10),
                findViewById(R.id.chip15), findViewById(R.id.chip20),
                findViewById(R.id.chip30), findViewById(R.id.chipCustom)
        };
        tvRingStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        tvStatePill.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    private void bindChips() {
        for (int i = 0; i < PRESETS.length; i++) {
            final int minutes = PRESETS[i];
            chips[i].setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                selectDuration(minutes);
            });
        }
        chips[chips.length - 1].setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showCustomDurationDialog();
        });
    }

    private void bindActions() {
        findViewById(R.id.btnSetup).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startActivity(new Intent(this, SetupActivity.class));
        });

        btnPrimary.setOnClickListener(v -> {
            if (Prefs.busy(this)) {
                return;
            }
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (isArmed()) {
                cancelTimer();
            } else {
                startTimer();
            }
        });

        btnResetNow.setOnClickListener(v -> {
            if (Prefs.busy(this)) {
                return;
            }
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            confirmResetNow();
        });

        switchRepeat.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setRepeat(this, checked);
            LogStore.add(LogStore.INFO, checked
                    ? "Repeat enabled — the timer re-arms itself after each reset."
                    : "Repeat disabled — one reset per start.");
            updateUi();
        });

        btnRefreshIp.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            refreshIp();
        });

        btnClearLog.setOnClickListener(v -> {
            LogStore.clear(this);
            renderLogs(true);
        });

        chipAccess.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            openAccessibilitySettings();
        });

        chipDirect.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startActivity(new Intent(this, SetupActivity.class));
        });

        tvFooter.setOnClickListener(v -> openHowItWorks());
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        long target = Prefs.targetAt(this);
        if (target > System.currentTimeMillis() && ResetScheduler.canExact(this)) {
            ResetScheduler.schedule(this, target);
        }
        refreshAccessChips();
        syncResultCard();
        renderLogs(false);
        updateUi();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        LogStore.setListener(null);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean isArmed() {
        return Prefs.targetAt(this) > System.currentTimeMillis();
    }

    private void startTimer() {
        int minutes = Prefs.durationMin(this);
        long target = System.currentTimeMillis() + minutes * 60_000L;
        Prefs.setCycleMin(this, minutes);
        Prefs.setTargetAt(this, target);
        ResetScheduler.schedule(this, target);
        ResetService.start(this, ResetService.ACTION_START, false);
        LogStore.add(LogStore.INFO, String.format(Locale.US,
                "Timer armed for %d min — reset at %s", minutes, TimeUtil.clockTime(target)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !ResetScheduler.canExact(this) && !exactDialogShown) {
            exactDialogShown = true;
            showExactAlarmDialog();
        }
        updateUi();
    }

    private void cancelTimer() {
        ResetService.cancel(this);
        ResetScheduler.cancel(this);
        Prefs.setTargetAt(this, 0L);
        LogStore.add(LogStore.INFO, "Timer cancelled");
        updateUi();
    }

    private void selectDuration(int minutes) {
        if (Prefs.busy(this)) {
            return;
        }
        Prefs.setDurationMin(this, minutes);
        boolean wasArmed = isArmed();
        if (wasArmed) {
            long target = System.currentTimeMillis() + minutes * 60_000L;
            Prefs.setCycleMin(this, minutes);
            Prefs.setTargetAt(this, target);
            ResetScheduler.schedule(this, target);
            ResetService.start(this, ResetService.ACTION_START, false);
            Toast.makeText(this, "Timer re-armed for " + minutes + " min", Toast.LENGTH_SHORT).show();
        }
        updateUi();
    }

    private void showCustomDurationDialog() {
        if (Prefs.busy(this)) {
            return;
        }
        int padding = dp(20);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(padding, dp(4), padding, 0);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Minutes (1–180)");
        input.setText(String.valueOf(Prefs.durationMin(this)));
        input.setSelectAllOnFocus(true);
        input.setTextSize(18);
        frame.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Custom duration")
                .setView(frame)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Set", (dialog, which) -> {
                    int minutes;
                    try {
                        minutes = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        minutes = -1;
                    }
                    if (minutes < 1 || minutes > 180) {
                        Toast.makeText(this, "Enter a number between 1 and 180",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectDuration(minutes);
                })
                .show();
    }

    private void confirmResetNow() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Run a reset now?")
                .setMessage("Mobile data and the hotspot will drop for roughly 20–40 seconds. "
                        + "Keep the phone unlocked if the Accessibility engine is in use.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset now", (dialog, which) -> {
                    ResetService.start(this, ResetService.ACTION_RUN_NOW, true);
                    LogStore.add(LogStore.INFO, "Manual reset requested");
                    updateUi();
                })
                .show();
    }

    private void showExactAlarmDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Allow exact alarms")
                .setMessage("Doze can delay the timer by a few minutes. Allow exact alarms so the "
                        + "Airplane switch happens on time.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        openAppSettings();
                    }
                })
                .show();
    }

    private void updateUi() {
        long now = System.currentTimeMillis();
        long target = Prefs.targetAt(this);
        boolean busy = Prefs.busy(this);
        boolean armed = target > now;
        int duration = Prefs.durationMin(this);

        boolean repeat = Prefs.repeat(this);
        if (switchRepeat.isChecked() != repeat) {
            switchRepeat.setOnCheckedChangeListener(null);
            switchRepeat.setChecked(repeat);
            switchRepeat.setOnCheckedChangeListener((button, checked) -> {
                Prefs.setRepeat(this, checked);
                LogStore.add(LogStore.INFO, checked
                        ? "Repeat enabled — the timer re-arms itself after each reset."
                        : "Repeat disabled — one reset per start.");
                updateUi();
            });
        }

        int mode;
        if (busy) {
            mode = 2;
            ring.setRunning(true);
            ring.setArmed(true);
            setPill(tvStatePill, "RUNNING", 2);
            tvRingLabel.setText("RESETTING");
            tvTime.setText("NOW");
            tvRingStatus.setText("Airplane mode is switching — the network will come back");
            tvNext.setText("Keep the phone unlocked until this finishes. Do not start another timer.");
            btnPrimary.setText("RESET IN PROGRESS");
            btnPrimary.setEnabled(false);
            btnPrimary.setAlpha(0.55f);
            btnResetNow.setEnabled(false);
            btnResetNow.setAlpha(0.55f);
            ring.setProgressImmediate(1f);
        } else if (armed) {
            mode = 1;
            long remaining = Math.max(0L, target - now);
            long cycleMs = Math.max(60_000L, Prefs.cycleMin(this) * 60_000L);
            float progress = Math.max(0f, Math.min(1f, (float) remaining / (float) cycleMs));
            ring.setRunning(false);
            ring.setArmed(true);
            if (lastMode != 1) {
                ring.setProgress(progress);
            } else {
                ring.setProgressImmediate(progress);
            }
            setPill(tvStatePill, "ARMED", 1);
            tvRingLabel.setText("NEXT RESET");
            tvTime.setText(TimeUtil.countdown(remaining));
            tvRingStatus.setText("Airplane toggles at " + TimeUtil.clockTime(target));
            tvNext.setText(repeat
                    ? "Repeats every " + duration + " min after each reset • cancel anytime"
                    : "One-shot reset • you can lock the screen now");
            btnPrimary.setText("CANCEL TIMER");
            btnPrimary.setEnabled(true);
            btnPrimary.setAlpha(1f);
            btnResetNow.setEnabled(true);
            btnResetNow.setAlpha(1f);
        } else {
            mode = 0;
            ring.setRunning(false);
            ring.setArmed(false);
            ring.setProgressImmediate(1f);
            long lastRun = Prefs.lastRunAt(this);
            if (lastRun > 0L) {
                setPill(tvStatePill, Prefs.lastSuccess(this) ? "DONE" : "CHECK",
                        Prefs.lastSuccess(this) ? 1 : 2);
            } else {
                setPill(tvStatePill, "IDLE", 0);
            }
            tvRingLabel.setText("READY TO ARM");
            tvTime.setText(TimeUtil.countdown(duration * 60_000L));
            tvRingStatus.setText(lastRun > 0L
                    ? "Last run " + TimeUtil.clockTime(lastRun)
                    : "No timer running");
            String lastMessage = Prefs.lastMessage(this);
            if (lastMessage != null && !lastMessage.isEmpty()) {
                tvNext.setText(lastMessage);
            } else {
                tvNext.setText("Pick a duration, start the timer, then lock the screen.");
            }
            btnPrimary.setText("START " + duration + " MIN TIMER");
            btnPrimary.setEnabled(true);
            btnPrimary.setAlpha(1f);
            btnResetNow.setEnabled(true);
            btnResetNow.setAlpha(1f);
        }
        lastMode = mode;

        updateChipSelection(duration);
        if (Prefs.lastRunAt(this) != lastSyncedRunAt) {
            syncResultCard();
        }
    }

    private void updateChipSelection(int duration) {
        boolean custom = true;
        for (int i = 0; i < PRESETS.length; i++) {
            boolean selected = PRESETS[i] == duration;
            chips[i].setSelected(selected);
            if (selected) {
                custom = false;
            }
        }
        TextView customChip = chips[chips.length - 1];
        customChip.setText(custom ? duration + " MIN" : "CUSTOM");
        customChip.setSelected(custom);
    }

    private void refreshAccessChips() {
        boolean accessibility = AccessibilityResetService.isEnabled(this);
        chipAccess.setText(accessibility ? "ACCESSIBILITY ON" : "ACCESSIBILITY OFF");
        chipAccess.setTextColor(ContextCompat.getColor(this,
                accessibility ? R.color.lime : R.color.muted));
        chipAccess.setContentDescription(accessibility
                ? "Accessibility is on. Tap to open Android accessibility settings."
                : "Accessibility is off. Tap to open Android accessibility settings.");

        boolean direct = ResetEngine.canWriteDirect(this);
        chipDirect.setText(direct ? "ADB DIRECT ON" : "ADB DIRECT OFF");
        chipDirect.setTextColor(ContextCompat.getColor(this,
                direct ? R.color.lime : R.color.muted));
        chipDirect.setContentDescription(direct
                ? "The ADB direct engine is granted. Tap to open setup."
                : "The ADB direct engine is not granted. Tap to open setup and copy the command.");
    }

    private void syncResultCard() {
        long runAt = Prefs.lastRunAt(this);
        lastSyncedRunAt = runAt;
        if (runAt <= 0L) {
            return;
        }
        String after = Prefs.lastIp(this);
        String before = Prefs.prevIp(this);
        if (after != null) {
            tvIp.setText(after);
            setPill(tvIpBadge, Prefs.lastChanged(this) ? "CHANGED" : "CURRENT",
                    Prefs.lastChanged(this) ? 1 : 0);
        }
        if (before != null && after != null) {
            if (before.equals(after)) {
                tvIpPrev.setText("Same address before and after • the carrier may reuse it "
                        + "(CGNAT). Retry, or toggle mobile data.");
            } else {
                tvIpPrev.setText("Previous " + before + " • the reset produced a new address.");
            }
        } else {
            String message = Prefs.lastMessage(this);
            if (message != null) {
                tvIpPrev.setText(message);
            }
        }
    }

    private void refreshIp() {
        if (Prefs.busy(this)) {
            Toast.makeText(this, "Wait for the reset to finish", Toast.LENGTH_SHORT).show();
            return;
        }
        setPill(tvIpBadge, "CHECKING", 2);
        btnRefreshIp.setEnabled(false);
        final String previous = Prefs.lastIp(this);
        IpUtil.fetchAsync(ip -> {
            btnRefreshIp.setEnabled(true);
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (ip == null) {
                setPill(tvIpBadge, "OFFLINE", 3);
                tvIp.setText("—");
                tvIpPrev.setText("No IP service responded. Check the connection and try again.");
                return;
            }
            tvIp.setText(ip);
            boolean changed = previous != null && !previous.equals(ip);
            setPill(tvIpBadge, changed ? "CHANGED" : "CURRENT", changed ? 1 : 0);
            if (previous == null) {
                tvIpPrev.setText("First check on this install. Stored locally to compare next time.");
            } else if (changed) {
                tvIpPrev.setText("Previous " + previous + " • the carrier handed out a new address.");
                Prefs.setPrevIp(this, previous);
            } else {
                tvIpPrev.setText("Unchanged from " + previous
                        + " • CGNAT can hand back the same address.");
            }
            Prefs.setLastIp(this, ip);
        });
    }

    private void renderLogs(boolean force) {
        if (logContainer == null) {
            return;
        }
        if (!force && LogStore.version() == lastRenderedLogVersion) {
            return;
        }
        lastRenderedLogVersion = LogStore.version();
        logContainer.removeAllViews();

        JSONArray items = LogStore.items();
        if (items.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Nothing yet. Arm the timer or run a reset.");
            empty.setTextSize(11);
            empty.setTextColor(ContextCompat.getColor(this, R.color.muted_dim));
            empty.setPadding(0, dp(6), 0, dp(6));
            logContainer.addView(empty);
            return;
        }

        int start = Math.max(0, items.length() - 10);
        for (int i = items.length() - 1; i >= start; i--) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                logContainer.addView(logRow(item));
            }
        }
    }

    private View logRow(JSONObject item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView time = new TextView(this);
        time.setText(TimeUtil.logTime(item.optLong("t")));
        time.setTextSize(9);
        time.setTypeface(Typeface.MONOSPACE);
        time.setTextColor(ContextCompat.getColor(this, R.color.muted_dim));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.rightMargin = dp(10);
        timeParams.topMargin = dp(1);
        row.addView(time, timeParams);

        TextView message = new TextView(this);
        message.setText(item.optString("m"));
        message.setTextSize(11);
        message.setLineSpacing(0f, 1.25f);
        message.setTextColor(ContextCompat.getColor(this, colorFor(item.optString("l"))));
        row.addView(message, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        return row;
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
        return R.color.text;
    }

    private void setPill(TextView view, String text, int tone) {
        int background = R.drawable.bg_pill;
        int foreground = R.color.muted;
        if (tone == 1) {
            background = R.drawable.bg_pill_ok;
            foreground = R.color.ok;
        } else if (tone == 2) {
            background = R.drawable.bg_pill_warn;
            foreground = R.color.warn;
        } else if (tone == 3) {
            background = R.drawable.bg_pill_bad;
            foreground = R.color.danger;
        }
        view.setText(text);
        view.setBackgroundResource(background);
        view.setTextColor(ContextCompat.getColor(this, foreground));
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Accessibility settings are not available on this device",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openHowItWorks() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(HOW_IT_WORKS)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser found. The guide lives in the repository docs.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
