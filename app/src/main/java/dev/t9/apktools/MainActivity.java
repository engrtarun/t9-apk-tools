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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

import java.util.List;
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
    private TextView chipShell;
    private TextView tvIpBadge;
    private TextView tvIp;
    private TextView tvIpPrev;
    private TextView tvIpMeta;
    private View ipCompare;
    private TextView tvIpBefore;
    private TextView tvIpBeforeAt;
    private TextView tvIpAfter;
    private TextView tvIpAfterAt;
    private TextView tvIpVerdict;
    private TextView tvIpArrow;
    private View btnIpDetails;
    private TextView tvIpDetailsLabel;
    private ImageView ivIpChevron;
    private View ipDetailsPanel;
    private LinearLayout ipDetailsList;
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
        try {
            setContentView(R.layout.activity_main);
            bindViews();
            bindChips();
            bindActions();

            ring.setProgress(1f);
            refreshAccessChips();
            syncResultCard();
            renderLogs(true);
            updateUi();
            reportLastCrash();
        } catch (Throwable error) {
            /* A launch crash is the one failure a user cannot report from inside
               the app. Draw a minimal recovery screen instead of dying, and keep
               the full trace on disk for the Copy button. */
            App.noteFailure("MainActivity.onCreate", error);
            showStartupFailure(error);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !Prefs.notifAsked(this)) {
            Prefs.setNotifAsked(this);
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        LogStore.setListener(() -> renderLogs(false));
        IpHistory.setListener(this::onIpHistoryLoaded);
    }

    /**
     * Last-resort screen used when the normal layout cannot be built. It still
     * gives the user a way to send the exact stack trace back.
     */
    private void showStartupFailure(Throwable error) {
        java.io.StringWriter writer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(writer));
        String trace = writer.toString();
        try {
            App.noteFailure("startup screen", error);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(24);
            box.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(this);
            title.setText("T9 APK Tools could not start");
            title.setTextSize(20);
            title.setTextColor(ContextCompat.getColor(this, R.color.text));
            box.addView(title);

            TextView body = new TextView(this);
            body.setText("The main screen failed to load. Your settings and history "
                    + "were not changed.\n\n" + trace);
            body.setTextSize(12);
            body.setTextColor(ContextCompat.getColor(this, R.color.muted));
            body.setPadding(0, dp(12), 0, dp(12));
            box.addView(body);

            TextView copy = new TextView(this);
            copy.setText("COPY LOG");
            copy.setTextSize(13);
            copy.setGravity(Gravity.CENTER);
            copy.setMinHeight(dp(48));
            copy.setTextColor(ContextCompat.getColor(this, R.color.lime));
            copy.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("T9 crash", trace));
                    Toast.makeText(this, "Crash log copied", Toast.LENGTH_SHORT).show();
                }
            });
            box.addView(copy);

            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            scroll.addView(box);
            setContentView(scroll);
        } catch (Throwable ignored) {
            Toast.makeText(this, "T9 APK Tools could not start", Toast.LENGTH_LONG).show();
        }
    }

    private void onIpHistoryLoaded() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        runOnUiThread(() -> {
            renderIpCard();
            maybeAutoCheckIp();
        });
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
        chipShell = findViewById(R.id.chipShell);
        tvIpBadge = findViewById(R.id.tvIpBadge);
        tvIp = findViewById(R.id.tvIp);
        tvIpPrev = findViewById(R.id.tvIpPrev);
        tvIpMeta = findViewById(R.id.tvIpMeta);
        ipCompare = findViewById(R.id.ipCompare);
        tvIpBefore = findViewById(R.id.tvIpBefore);
        tvIpBeforeAt = findViewById(R.id.tvIpBeforeAt);
        tvIpAfter = findViewById(R.id.tvIpAfter);
        tvIpAfterAt = findViewById(R.id.tvIpAfterAt);
        tvIpVerdict = findViewById(R.id.tvIpVerdict);
        tvIpArrow = findViewById(R.id.tvIpArrow);
        btnIpDetails = findViewById(R.id.btnIpDetails);
        tvIpDetailsLabel = findViewById(R.id.tvIpDetailsLabel);
        ivIpChevron = findViewById(R.id.ivIpChevron);
        ipDetailsPanel = findViewById(R.id.ipDetailsPanel);
        ipDetailsList = findViewById(R.id.ipDetailsList);
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
            refreshIp(false);
        });

        btnIpDetails.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            toggleIpDetails();
        });

        findViewById(R.id.btnAllRuns).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startActivity(new Intent(this, HistoryActivity.class));
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

        chipShell.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startActivity(new Intent(this, SetupActivity.class));
        });

        tvFooter.setOnClickListener(v -> openHowItWorks());
        bindBuildLabel();
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
        maybeAutoCheckIp();
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
        IpHistory.setListener(null);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean isArmed() {
        return Prefs.targetAt(this) > System.currentTimeMillis();
    }

    private void startTimer() {
        if (!hasEngine()) {
            showEngineDialog();
            return;
        }
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
        if (!hasEngine()) {
            showEngineDialog();
            return;
        }
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

    private boolean hasEngine() {
        return AccessibilityResetService.isEnabled(this)
                || ResetEngine.canWriteDirect(this)
                || ShellEngine.available();
    }

    private void showEngineDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("No engine is enabled")
                .setMessage("The app needs one way to reset the network. Connect Shizuku for "
                        + "the no-Airplane data engine, grant the one-time ADB permission for "
                        + "the Direct engine, or enable the Accessibility service in Setup. "
                        + "Direct and Shizuku both work on a locked phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open Setup", (dialog, which) ->
                        startActivity(new Intent(this, SetupActivity.class)))
                .show();
    }

    /** Shows the stack trace saved by the crash handler on the previous launch. */
    private void bindBuildLabel() {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            TextView build = findViewById(R.id.tvBuild);
            build.setText("v" + info.versionName + " (" + info.versionCode
                    + ") · No root · No ads · No analytics");
        } catch (Exception ignored) {
        }
    }

    private void reportLastCrash() {
        java.io.File file = new java.io.File(getFilesDir(), "last-crash.txt");
        if (!file.exists()) {
            return;
        }
        String text;
        try {
            byte[] raw = new byte[(int) file.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            try {
                int read = in.read(raw);
                text = new String(raw, 0, Math.max(0, read),
                        java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            file.delete();
            return;
        }
        file.delete();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > 1400) {
            text = text.substring(0, 1400) + "\n…";
        }
        final String report = text;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Last launch crashed")
                .setMessage(report)
                .setNegativeButton("Close", null)
                .setPositiveButton("Copy log", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("T9 crash", report));
                        Toast.makeText(this, "Crash log copied", Toast.LENGTH_SHORT).show();
                    }
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

        boolean shell = ShellEngine.available();
        chipShell.setText(shell ? "SHIZUKU ON" : "SHIZUKU OFF");
        chipShell.setTextColor(ContextCompat.getColor(this,
                shell ? R.color.lime : R.color.muted));
        chipShell.setContentDescription(shell
                ? "The Shizuku data engine is connected. Tap to open setup."
                : "The Shizuku data engine is not connected. Tap to open setup and connect it.");
    }

    private void syncResultCard() {
        lastSyncedRunAt = Prefs.lastRunAt(this);
        renderIpCard();
    }

    /**
     * Draws the Public IP card from the on-device ledger. Every value shown here was
     * fetched from the network, so the card can answer "what was it before, and what
     * did the reset actually produce".
     */
    private void renderIpCard() {
        List<IpHistory.Entry> history = IpHistory.all(this);
        IpHistory.Entry latest = history.isEmpty() ? null : history.get(0);
        IpHistory.Entry after = IpHistory.newestOf(this, IpHistory.KIND_AFTER);
        IpHistory.Entry before = IpHistory.partnerBefore(this);

        if (latest != null) {
            tvIp.setText(latest.ip);
            tvIpMeta.setText("Checked " + TimeUtil.clockTime(latest.at)
                    + " · stored on this phone only");
        } else {
            tvIp.setText("—");
            tvIpMeta.setText("No check yet");
        }

        if (after != null && before != null) {
            boolean changed = !before.ip.equals(after.ip);
            ipCompare.setVisibility(View.VISIBLE);
            tvIpBefore.setText(before.ip);
            tvIpBeforeAt.setText(TimeUtil.clockTime(before.at));
            tvIpAfter.setText(after.ip);
            tvIpAfterAt.setText(TimeUtil.clockTime(after.at));
            setPill(tvIpVerdict, changed ? "CHANGED" : "SAME", changed ? 1 : 2);
            setPill(tvIpBadge, changed ? "CHANGED" : "SAME", changed ? 1 : 2);
            tvIpArrow.setTextColor(ContextCompat.getColor(this,
                    changed ? R.color.lime : R.color.muted_dim));
            tvIpPrev.setText(changed
                    ? "Both readings came from the carrier, not from this phone's settings."
                    : "The carrier reused the same address (CGNAT). Retry, or toggle mobile data.");
        } else {
            ipCompare.setVisibility(View.GONE);
            if (latest == null) {
                setPill(tvIpBadge, "NOT CHECKED", 0);
                tvIpPrev.setText("Tap refresh. The app also reads the address once when it opens.");
            } else if (IpHistory.KIND_BEFORE.equals(latest.kind)) {
                setPill(tvIpBadge, "BEFORE", 0);
                tvIpPrev.setText("This was the address before a reset. The after value appears "
                        + "when the reset finishes.");
            } else {
                setPill(tvIpBadge, "CURRENT", 0);
                tvIpPrev.setText("Read from the carrier edge. Tap refresh to read it again.");
            }
        }

        if (ipDetailsPanel.getVisibility() == View.VISIBLE) {
            renderIpDetails(history);
        }
    }

    private void maybeAutoCheckIp() {
        if (Prefs.busy(this) || !IpHistory.isLoaded()) {
            return;
        }
        IpHistory.Entry latest = IpHistory.newest(this);
        long age = latest == null ? Long.MAX_VALUE : System.currentTimeMillis() - latest.at;
        if (age < 5 * 60 * 1000L) {
            return;
        }
        if (!IpUtil.isOnline(this)) {
            return;
        }
        refreshIp(true);
    }

    private void toggleIpDetails() {
        boolean open = ipDetailsPanel.getVisibility() != View.VISIBLE;
        ipDetailsPanel.setVisibility(open ? View.VISIBLE : View.GONE);
        ivIpChevron.animate().rotation(open ? 180f : 0f).setDuration(160).start();
        tvIpDetailsLabel.setText(open ? "HIDE" : "DETAILS");
        btnIpDetails.setContentDescription(open
                ? "Hide the recent public IP checks"
                : "Show the recent public IP checks");
        if (open) {
            renderIpDetails(IpHistory.all(this));
        }
    }

    private void renderIpDetails(List<IpHistory.Entry> history) {
        ipDetailsList.removeAllViews();
        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing recorded yet.");
            empty.setTextSize(11);
            empty.setTextColor(ContextCompat.getColor(this, R.color.muted_dim));
            empty.setPadding(0, dp(6), 0, dp(6));
            ipDetailsList.addView(empty);
            return;
        }
        int shown = Math.min(history.size(), 8);
        for (int i = 0; i < shown; i++) {
            ipDetailsList.addView(ipDetailRow(history.get(i)));
        }
    }

    private View ipDetailRow(IpHistory.Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView time = new TextView(this);
        time.setText(TimeUtil.clockTime(entry.at));
        time.setTextSize(10);
        time.setTypeface(Typeface.MONOSPACE);
        time.setTextColor(ContextCompat.getColor(this, R.color.muted_dim));
        time.setWidth(dp(62));
        row.addView(time);

        TextView kind = new TextView(this);
        kind.setText(kindLabel(entry.kind));
        kind.setTextSize(9);
        kind.setLetterSpacing(0.08f);
        kind.setAllCaps(true);
        kind.setTextColor(ContextCompat.getColor(this, kindColor(entry.kind)));
        kind.setWidth(dp(66));
        row.addView(kind);

        TextView ip = new TextView(this);
        ip.setText(entry.ip);
        ip.setTextSize(12);
        ip.setTypeface(Typeface.MONOSPACE);
        ip.setSingleLine(true);
        ip.setEllipsize(TextUtils.TruncateAt.END);
        ip.setTextColor(ContextCompat.getColor(this, R.color.text));
        row.addView(ip, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        return row;
    }

    private String kindLabel(String kind) {
        if (IpHistory.KIND_BEFORE.equals(kind)) {
            return "Before";
        }
        if (IpHistory.KIND_AFTER.equals(kind)) {
            return "After";
        }
        if (IpHistory.KIND_AUTO.equals(kind)) {
            return "Auto";
        }
        return "Manual";
    }

    private int kindColor(String kind) {
        if (IpHistory.KIND_AFTER.equals(kind)) {
            return R.color.lime;
        }
        if (IpHistory.KIND_BEFORE.equals(kind)) {
            return R.color.muted;
        }
        return R.color.text;
    }

    private void refreshIp(boolean silent) {
        if (Prefs.busy(this)) {
            if (!silent) {
                Toast.makeText(this, "Wait for the reset to finish", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        setPill(tvIpBadge, "CHECKING", 2);
        btnRefreshIp.setEnabled(false);
        IpUtil.fetchAsync(ip -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            btnRefreshIp.setEnabled(true);
            if (ip == null) {
                setPill(tvIpBadge, "OFFLINE", 3);
                tvIpPrev.setText("No IP service responded. Check the connection and try again.");
                return;
            }
            IpHistory.add(this, ip, silent ? IpHistory.KIND_AUTO : IpHistory.KIND_MANUAL);
            renderIpCard();
            if (!silent) {
                Toast.makeText(this, "Public IP refreshed", Toast.LENGTH_SHORT).show();
            }
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
