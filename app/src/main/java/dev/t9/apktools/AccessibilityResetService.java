package dev.t9.apktools;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AccessibilityResetService extends AccessibilityService {

    private static volatile AccessibilityResetService instance;

    public static AccessibilityResetService instance() {
        return instance;
    }

    public static boolean isConnected() {
        return instance != null;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        LogStore.add(LogStore.INFO, "Accessibility engine connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static boolean isEnabled(Context context) {
        if (instance != null) {
            return true;
        }
        AccessibilityManager manager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        if (manager != null) {
            List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (services != null) {
                for (AccessibilityServiceInfo info : services) {
                    ResolveInfo resolveInfo = info.getResolveInfo();
                    if (resolveInfo != null && resolveInfo.serviceInfo != null
                            && context.getPackageName().equals(resolveInfo.serviceInfo.packageName)) {
                        return true;
                    }
                }
            }
        }
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null
                && enabled.contains(context.getPackageName())
                && enabled.contains("AccessibilityResetService");
    }

    public static boolean awaitConnected(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (instance == null && System.currentTimeMillis() < end) {
            sleep(100);
        }
        return instance != null;
    }

    public boolean resetViaSettings(int offDelaySec) {
        boolean before = isAirplaneOn();
        try {
            if (!launchAirplaneSettings()) {
                LogStore.add(LogStore.ERR, "Could not open Airplane mode settings");
                return false;
            }
            sleep(1600);
            boolean first = waitForToggle(!before, 7000);
            if (!first) {
                LogStore.add(LogStore.WARN, "Airplane switch not found in Settings");
                return false;
            }
            LogStore.add(LogStore.INFO, "Airplane mode switched on (Settings)");
            sleep(offDelaySec * 1000L);
            boolean second = waitForToggle(before, 7000);
            if (second) {
                LogStore.add(LogStore.OK, "Airplane mode restored (Settings)");
            }
            return second;
        } catch (Exception e) {
            LogStore.add(LogStore.ERR, "Settings automation failed: " + e.getClass().getSimpleName());
            return false;
        } finally {
            pressBack();
            sleep(350);
            pressBack();
        }
    }

    public boolean resetViaQuickSettings(int offDelaySec, float xPercent, float yPercent) {
        boolean before = isAirplaneOn();
        try {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
            sleep(1100);
            boolean first = waitForToggle(!before, 2600);
            if (!first) {
                tapPercent(xPercent, yPercent);
                sleep(900);
                first = isAirplaneOn() != before;
            }
            if (!first) {
                LogStore.add(LogStore.WARN, "Airplane tile not found in Quick Settings");
                return false;
            }
            LogStore.add(LogStore.INFO, "Airplane mode switched on (Quick Settings)");
            sleep(offDelaySec * 1000L);

            boolean second = isAirplaneOn() == before;
            if (!second) {
                second = waitForToggle(before, 2000);
            }
            if (!second) {
                tapPercent(xPercent, yPercent);
                sleep(900);
                second = isAirplaneOn() == before;
            }
            if (!second) {
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                sleep(900);
                second = waitForToggle(before, 2600);
                if (!second) {
                    tapPercent(xPercent, yPercent);
                    sleep(900);
                    second = isAirplaneOn() == before;
                }
            }
            if (second) {
                LogStore.add(LogStore.OK, "Airplane mode restored (Quick Settings)");
            }
            return second;
        } catch (Exception e) {
            LogStore.add(LogStore.ERR, "Quick Settings automation failed: "
                    + e.getClass().getSimpleName());
            return false;
        } finally {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    public boolean enforceAirplaneState(boolean desired) {
        if (isAirplaneOn() == desired) {
            return true;
        }
        try {
            if (!launchAirplaneSettings()) {
                return false;
            }
            sleep(1300);
            return waitForToggle(desired, 7000);
        } catch (Exception e) {
            return false;
        } finally {
            pressBack();
            sleep(300);
            pressBack();
        }
    }

    public boolean enableHotspotIfOff() {
        try {
            Intent intent = new Intent("android.settings.TETHER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            return false;
        }
        sleep(1700);
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            AccessibilityNodeInfo toggle = findCheckableWithLabel(root, "hotspot");
            if (toggle == null) {
                AccessibilityNodeInfo entry = findClickableWithLabel(root, "hotspot");
                if (entry != null) {
                    clickNode(entry);
                    sleep(1400);
                    root = getRootInActiveWindow();
                    toggle = findCheckableWithLabel(root, "hotspot");
                }
            }
            if (toggle == null) {
                return false;
            }
            if (toggle.isChecked()) {
                return true;
            }
            clickNode(toggle);
            sleep(1700);
            root = getRootInActiveWindow();
            AccessibilityNodeInfo verified = findCheckableWithLabel(root, "hotspot");
            return verified != null && verified.isChecked();
        } catch (Exception e) {
            return false;
        } finally {
            pressBack();
        }
    }

    private boolean launchAirplaneSettings() {
        Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForToggle(boolean desired, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        int clicks = 0;
        int emptyLoops = 0;
        boolean scrolled = false;
        while (System.currentTimeMillis() < end) {
            if (isAirplaneOn() == desired) {
                return true;
            }
            List<AccessibilityNodeInfo> candidates = findAirplaneCandidates();
            if (candidates.isEmpty()) {
                emptyLoops++;
                if (emptyLoops >= 3 && !scrolled) {
                    scrollForward();
                    scrolled = true;
                    sleep(500);
                } else {
                    sleep(350);
                }
                continue;
            }
            for (AccessibilityNodeInfo candidate : candidates) {
                if (clickNode(candidate)) {
                    clicks++;
                    sleep(900);
                    if (isAirplaneOn() == desired) {
                        return true;
                    }
                    if (clicks >= 5) {
                        return false;
                    }
                }
            }
            sleep(300);
        }
        return isAirplaneOn() == desired;
    }

    private List<AccessibilityNodeInfo> findAirplaneCandidates() {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AccessibilityNodeInfo root : roots()) {
            collectAirplaneCandidates(root, 0, result, seen);
        }
        return result;
    }

    private void collectAirplaneCandidates(AccessibilityNodeInfo node, int depth,
                                           List<AccessibilityNodeInfo> out, Set<String> seen) {
        if (node == null || depth > 50 || out.size() > 12) {
            return;
        }
        if (matchesAirplane(node)) {
            addCandidate(node, out, seen);
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null) {
                addCandidate(parent, out, seen);
                for (int i = 0; i < parent.getChildCount() && i < 12; i++) {
                    AccessibilityNodeInfo sibling = parent.getChild(i);
                    if (sibling != null && (sibling.isCheckable() || sibling.isClickable())) {
                        addCandidate(sibling, out, seen);
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount() && i < 80; i++) {
            collectAirplaneCandidates(node.getChild(i), depth + 1, out, seen);
        }
    }

    private boolean matchesAirplane(AccessibilityNodeInfo node) {
        String text = lower(node.getText());
        String description = lower(node.getContentDescription());
        return text.contains("airplane") || text.contains("flight mode")
                || description.contains("airplane") || description.contains("flight mode");
    }

    private void addCandidate(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out,
                              Set<String> seen) {
        if (node == null) {
            return;
        }
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 5 && current != null; i++) {
            if (current.isClickable() || current.isCheckable()) {
                String key = keyOf(current);
                if (seen.add(key)) {
                    out.add(current);
                }
                return;
            }
            current = current.getParent();
        }
        if (node.isCheckable()) {
            String key = keyOf(node);
            if (seen.add(key)) {
                out.add(node);
            }
        }
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SELECT)) {
            return true;
        }
        AccessibilityNodeInfo parent = node.getParent();
        for (int i = 0; i < 4 && parent != null; i++) {
            if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private AccessibilityNodeInfo findCheckableWithLabel(AccessibilityNodeInfo root, String needle) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collectAll(root, 0, all);
        for (AccessibilityNodeInfo node : all) {
            if (node.isCheckable() && hasLabel(node, needle)) {
                return node;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findClickableWithLabel(AccessibilityNodeInfo root, String needle) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collectAll(root, 0, all);
        for (AccessibilityNodeInfo node : all) {
            if (node.isClickable() && (contains(node.getText(), needle)
                    || contains(node.getContentDescription(), needle))) {
                return node;
            }
        }
        return null;
    }

    private boolean hasLabel(AccessibilityNodeInfo node, String needle) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 5 && current != null; i++) {
            if (contains(current.getText(), needle)
                    || contains(current.getContentDescription(), needle)) {
                return true;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (parent != null) {
                for (int j = 0; j < parent.getChildCount() && j < 10; j++) {
                    AccessibilityNodeInfo sibling = parent.getChild(j);
                    if (sibling != null && (contains(sibling.getText(), needle)
                            || contains(sibling.getContentDescription(), needle))) {
                        return true;
                    }
                }
            }
            current = parent;
        }
        return false;
    }

    private void collectAll(AccessibilityNodeInfo node, int depth, List<AccessibilityNodeInfo> out) {
        if (node == null || depth > 40 || out.size() > 700) {
            return;
        }
        out.add(node);
        for (int i = 0; i < node.getChildCount() && i < 80; i++) {
            collectAll(node.getChild(i), depth + 1, out);
        }
    }

    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            roots.add(active);
        }
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null) {
                        roots.add(root);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return roots;
    }

    private void scrollForward() {
        for (AccessibilityNodeInfo root : roots()) {
            List<AccessibilityNodeInfo> all = new ArrayList<>();
            collectAll(root, 0, all);
            for (AccessibilityNodeInfo node : all) {
                if (node.isScrollable()) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                        return;
                    }
                }
            }
        }
    }

    private void tapPercent(float xPercent, float yPercent) {
        float[] size = screenSize();
        float x = Math.max(1f, Math.min(size[0] - 1f, size[0] * xPercent / 100f));
        float y = Math.max(1f, Math.min(size[1] - 1f, size[1] * yPercent / 100f));
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 60L))
                .build();
        final CountDownLatch latch = new CountDownLatch(1);
        boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription description) {
                latch.countDown();
            }

            @Override
            public void onCancelled(GestureDescription description) {
                latch.countDown();
            }
        }, null);
        if (dispatched) {
            try {
                latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private float[] screenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (manager != null) {
                Rect bounds = manager.getCurrentWindowMetrics().getBounds();
                return new float[]{bounds.width(), bounds.height()};
            }
        }
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (manager != null) {
            manager.getDefaultDisplay().getRealMetrics(metrics);
        }
        return new float[]{metrics.widthPixels, metrics.heightPixels};
    }

    private void pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private boolean isAirplaneOn() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    private String keyOf(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return node.getClassName() + "|" + bounds + "|" + node.getText()
                + "|" + node.getContentDescription();
    }

    private String lower(CharSequence value) {
        return value == null ? "" : value.toString().toLowerCase();
    }

    private boolean contains(CharSequence value, String needle) {
        return value != null && value.toString().toLowerCase().contains(needle);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
