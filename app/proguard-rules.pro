# Keep the components referenced from the manifest and from XML.
-keep class dev.t9.apktools.AccessibilityResetService { *; }
-keep class dev.t9.apktools.ResetService { *; }
-keep class dev.t9.apktools.TimerRingView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

