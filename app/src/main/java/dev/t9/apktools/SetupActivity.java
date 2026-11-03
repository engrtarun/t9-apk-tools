package dev.t9.apktools;

import android.Manifest;
import android.app.AlarmManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import rikka.shizuku.Shizuku;

public class SetupActivity extends AppCompatActivity {

    private TextView tvAccessStatus;
    private TextView tvNotifStatus;
    private TextView tvAlarmStatus;
    private TextView tvBatteryStatus;
    private TextView tvDirectStatus;
    private TextView tvShellStatus;
    private TextView tvStrategyHelp;
    private TextView tvOffDelay;
    private TextView tvQsX;
    private TextView tvQsY;
    private TextView tvAdbCommand;
    private TextView tvVersion;

    private RadioGroup rgStrategy;
    private SeekBar seekOffDelay;
    private SeekBar seekQsX;
    private SeekBar seekQsY;
    private SwitchCompat switchWake;
    private SwitchCompat switchVerify;
    private SwitchCompat switchHotspot;

    private boolean loading = true;

    private static final int SHELL_PERMISSION_REQUEST = 2910;

    private final Shizuku.OnRequestPermissionResultListener shellPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHELL_PERMISSION_REQUEST) {
                    return;
                }
                boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
                LogStore.add(LogStore.INFO, granted
                        ? "Shizuku grant accepted — the data engine is ready"
                        : "Shizuku grant refused");
                refreshStatus();
            };

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                refreshStatus();
                if (!granted) {
                    Toast.makeText(this, "Notifications stay off. The timer still works.",
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        bindViews();
        loadPreferences();
        bindActions();
        refreshStatus();
        setVersionLabel();
    }

    private void bindViews() {
        tvAccessStatus = findViewById(R.id.tvAccessStatus);
        tvNotifStatus = findViewById(R.id.tvNotifStatus);
        tvAlarmStatus = findViewById(R.id.tvAlarmStatus);
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        tvDirectStatus = findViewById(R.id.tvDirectStatus);
        tvShellStatus = findViewById(R.id.tvShellStatus);
        tvStrategyHelp = findViewById(R.id.tvStrategyHelp);
        tvOffDelay = findViewById(R.id.tvOffDelay);
        tvQsX = findViewById(R.id.tvQsX);
        tvQsY = findViewById(R.id.tvQsY);
        tvAdbCommand = findViewById(R.id.tvAdbCommand);
        tvVersion = findViewById(R.id.tvVersion);
        rgStrategy = findViewById(R.id.rgStrategy);
        seekOffDelay = findViewById(R.id.seekOffDelay);
        seekQsX = findViewById(R.id.seekQsX);
        seekQsY = findViewById(R.id.seekQsY);
        switchWake = findViewById(R.id.switchWake);
        switchVerify = findViewById(R.id.switchVerify);
        switchHotspot = findViewById(R.id.switchHotspot);
    }

    private void loadPreferences() {
        loading = true;

        String strategy = Prefs.strategy(this);
        switch (strategy) {
            case Prefs.STRATEGY_DIRECT:
                rgStrategy.check(R.id.rbDirect);
                break;
            case Prefs.STRATEGY_SETTINGS:
                rgStrategy.check(R.id.rbSettings);
                break;
            case Prefs.STRATEGY_QS:
                rgStrategy.check(R.id.rbQs);
                break;
            case Prefs.STRATEGY_DATA:
                rgStrategy.check(R.id.rbData);
                break;
            default:
                rgStrategy.check(R.id.rbAuto);
                break;
        }
        tvStrategyHelp.setText(strategyHelp(strategy));

        int offDelay = Prefs.offDelaySec(this);
        seekOffDelay.setProgress(offDelay - 2);
        tvOffDelay.setText(offDelay + " s");

        int qsX = Math.max(1, Math.min(99, Math.round(Prefs.qsXPercent(this))));
        int qsY = Math.max(1, Math.min(99, Math.round(Prefs.qsYPercent(this))));
        seekQsX.setProgress(qsX);
        seekQsY.setProgress(qsY);
        tvQsX.setText(qsX + " %");
        tvQsY.setText(qsY + " %");

        switchWake.setChecked(Prefs.wakeScreen(this));
        switchVerify.setChecked(Prefs.verifyIp(this));
        switchHotspot.setChecked(Prefs.restoreHotspot(this));

        loading = false;
    }

    private void bindActions() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnAccess).setOnClickListener(v -> openAccessibilityWithHint());

        findViewById(R.id.btnNotif).setOnClickListener(v -> openNotificationSettings());

        findViewById(R.id.btnAlarm).setOnClickListener(v -> openExactAlarmSettings());

        findViewById(R.id.btnBattery).setOnClickListener(v -> openBatterySettings());

        rgStrategy.setOnCheckedChangeListener((group, checkedId) -> {
            if (loading) {
                return;
            }
            String strategy = strategyFor(checkedId);
            Prefs.setStrategy(this, strategy);
            tvStrategyHelp.setText(strategyHelp(strategy));
            LogStore.add(LogStore.INFO, "Reset engine set to " + strategy);
        });

        seekOffDelay.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int seconds = progress + 2;
                tvOffDelay.setText(seconds + " s");
                if (!loading) {
                    Prefs.setOffDelaySec(SetupActivity.this, seconds);
                }
            }
        });

        seekQsX.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = Math.max(1, Math.min(99, progress));
                tvQsX.setText(value + " %");
                if (!loading) {
                    Prefs.setQsXPercent(SetupActivity.this, value);
                }
            }
        });

        seekQsY.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = Math.max(1, Math.min(99, progress));
                tvQsY.setText(value + " %");
                if (!loading) {
                    Prefs.setQsYPercent(SetupActivity.this, value);
                }
            }
        });

        switchWake.setOnCheckedChangeListener((button, checked) -> {
            if (!loading) {
                Prefs.setWakeScreen(this, checked);
                LogStore.add(LogStore.INFO, checked
                        ? "Wake screen is on"
                        : "Wake screen is off — Accessibility needs an unlocked screen");
            }
        });

        switchVerify.setOnCheckedChangeListener((button, checked) -> {
            if (!loading) {
                Prefs.setVerifyIp(this, checked);
                LogStore.add(LogStore.INFO, checked
                        ? "IP verification is on"
                        : "IP verification is off");
            }
        });

        switchHotspot.setOnCheckedChangeListener((button, checked) -> {
            if (loading) {
                return;
            }
            Prefs.setRestoreHotspot(this, checked);
            if (checked && !AccessibilityResetService.isEnabled(this)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Accessibility is off")
                        .setMessage("Hotspot restore uses the accessibility engine. Turn the service "
                                + "on, or Android will not let the app flip the hotspot switch.")
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Open accessibility", (d, w) -> openAccessibilityWithHint())
                        .show();
            }
            LogStore.add(LogStore.INFO, checked
                    ? "Hotspot restore is on (experimental)"
                    : "Hotspot restore is off");
        });

        findViewById(R.id.btnCopyAdb).setOnClickListener(v -> copyAdbCommand());

        findViewById(R.id.btnTestReset).setOnClickListener(v -> confirmTestReset());

        findViewById(R.id.btnResetAll).setOnClickListener(v -> confirmResetAll());

        tvAdbCommand.setOnLongClickListener(v -> {
            copyAdbCommand();
            return true;
        });

        findViewById(R.id.btnConnectShizuku).setOnClickListener(v -> connectShizuku());
    }

    /** Connects the optional Shizuku helper that powers the no-Airplane data engine. */
    private void connectShizuku() {
        if (!ShellEngine.binderAlive()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Shizuku is not running")
                    .setMessage("Shizuku is a small, open-source helper that lets this app run "
                            + "two commands with the ADB identity — that is how the data engine "
                            + "resets mobile data without Airplane mode. Install Shizuku, start it "
                            + "once (wireless debugging works on Android 11+, or one ADB command "
                            + "from a PC), then come back and tap CONNECT SHIZUKU.")
                    .setNegativeButton("Later", null)
                    .setPositiveButton("Download page", (d, w) -> {
                        try {
                            startActivity(new android.content.Intent(Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://shizuku.rikka.app/download/")));
                        } catch (Exception ignored) {
                        }
                    })
                    .show();
            return;
        }
        if (ShellEngine.granted()) {
            Toast.makeText(this, "Shizuku is already granted.", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(this, "T9 was denied in Shizuku. Open Shizuku and allow it "
                        + "under Recent apps.", Toast.LENGTH_LONG).show();
                return;
            }
            Shizuku.requestPermission(SHELL_PERMISSION_REQUEST);
        } catch (Throwable t) {
            Toast.makeText(this, "Shizuku refused: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String strategyFor(int checkedId) {
        if (checkedId == R.id.rbDirect) {
            return Prefs.STRATEGY_DIRECT;
        }
        if (checkedId == R.id.rbSettings) {
            return Prefs.STRATEGY_SETTINGS;
        }
        if (checkedId == R.id.rbQs) {
            return Prefs.STRATEGY_QS;
        }
        if (checkedId == R.id.rbData) {
            return Prefs.STRATEGY_DATA;
        }
        return Prefs.STRATEGY_AUTO;
    }

    private String strategyHelp(String strategy) {
        switch (strategy) {
            case Prefs.STRATEGY_DIRECT:
                return "Writes the system Airplane setting directly. Needs the one-time ADB grant, "
                        + "works with the screen off, and is the most reliable option.";
            case Prefs.STRATEGY_SETTINGS:
                return "Opens the Airplane mode page and taps the switch through Accessibility. "
                        + "No laptop needed, but the phone must be unlocked.";
            case Prefs.STRATEGY_QS:
                return "Pulls down Quick Settings and taps the Airplane tile. Fast, but tile "
                        + "layout differs by OEM — calibrate the coordinates below if needed.";
            case Prefs.STRATEGY_DATA:
                return "Runs the mobile data reset through the Shizuku helper: the data "
                        + "connection is torn down and re-registered, which is what makes the "
                        + "carrier reuse or hand out a mapping. Airplane mode, Wi-Fi, and the "
                        + "hotspot are never touched. Needs Shizuku installed, running, and granted once. Works with the screen off.";
            default:
                return "Uses the Shizuku data engine first if it is connected (no Airplane mode), "
                        + "then Direct, then Quick Settings and the Settings page. Best balance for most phones.";
        }
    }

    private void refreshStatus() {
        boolean accessibility = AccessibilityResetService.isEnabled(this);
        setPill(tvAccessStatus, accessibility ? "ON" : "OFF", accessibility ? 1 : 0);

        boolean notifications = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifications = false;
        }
        setPill(tvNotifStatus, notifications ? "ON" : "OFF", notifications ? 1 : 2);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setPill(tvAlarmStatus, "DEFAULT", 0);
        } else {
            boolean exact = ResetScheduler.canExact(this);
            setPill(tvAlarmStatus, exact ? "ON" : "OFF", exact ? 1 : 2);
        }

        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null && power.isIgnoringBatteryOptimizations(getPackageName())) {
            setPill(tvBatteryStatus, "EXEMPT", 1);
        } else {
            setPill(tvBatteryStatus, "OPTIMIZED", 2);
        }

        boolean direct = ResetEngine.canWriteDirect(this);
        setPill(tvDirectStatus, direct ? "GRANTED" : "NOT GRANTED", direct ? 1 : 0);

        String shell = ShellEngine.statusLabel();
        int shellState = ShellEngine.available() ? 1 : 2;
        setPill(tvShellStatus, shell, shellState);
    }

    private void setVersionLabel() {
        String version = "1.0.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        tvVersion.setText("T9 APK Tools " + version + " • native Java • no root");
    }

    private void openAccessibilityWithHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !Prefs.accessibilityHintShown(this)) {
            Prefs.setAccessibilityHintShown(this);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("If the toggle is greyed out")
                    .setMessage("Android 13 and newer can block Accessibility for apps installed "
                            + "outside a store. If that happens: open App info, tap the ⋮ menu, "
                            + "choose “Allow restricted settings”, then come back here.")
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("App info", (d, w) -> openAppInfo())
                    .setPositiveButton("Continue", (d, w) -> openAccessibility())
                    .show();
        } else {
            openAccessibility();
        }
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Accessibility settings are not available",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            openAppInfo();
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "Exact alarms are allowed by default on this Android version",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            openAppInfo();
        }
    }

    private void openBatterySettings() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        boolean exempt = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        try {
            if (exempt) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } else {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            }
        } catch (Exception e) {
            openAppInfo();
        }
    }

    private void openAppInfo() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
        }
    }

    private void copyAdbCommand() {
        String command = tvAdbCommand.getText().toString().trim();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ADB grant command", command));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "ADB command copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmTestReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Run a reset now?")
                .setMessage("Mobile data and the hotspot will drop for 20–40 seconds. "
                        + "This does not cancel an armed timer.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Run", (dialog, which) -> {
                    ResetService.start(this, ResetService.ACTION_RUN_NOW, true);
                    LogStore.add(LogStore.INFO, "Test reset requested from Setup");
                    Toast.makeText(this, "Reset started — watch the main screen",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    private void confirmResetAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset everything?")
                .setMessage("This stops the timer, clears the activity log, and puts every setting "
                        + "back to its default. The ADB grant and Android permissions stay as they are.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    ResetScheduler.cancel(this);
                    ResetService.cancel(this);
                    stopService(new Intent(this, ResetService.class));
                    Prefs.setTargetAt(this, 0L);
                    Prefs.reset(this);
                    LogStore.clear(this);
                    Toast.makeText(this, "Settings cleared", Toast.LENGTH_SHORT).show();
                    recreate();
                })
                .show();
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

    @Override
    protected void onStart() {
        super.onStart();
        Shizuku.addRequestPermissionResultListener(shellPermissionListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Shizuku.removeRequestPermissionResultListener(shellPermissionListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (!loading) {
            // Keep the strategy label in sync if prefs were changed elsewhere.
            String strategy = Prefs.strategy(this);
            tvStrategyHelp.setText(strategyHelp(strategy));
        }
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
