package dev.t9.apktools;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

public class TimerRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float displayProgress = 1f;
    private ValueAnimator animator;
    private boolean armed;
    private boolean running;

    private int lime;
    private int track;
    private int inner;

    public TimerRingView(Context context) {
        this(context, null);
    }

    public TimerRingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimerRingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        lime = ContextCompat.getColor(context, R.color.lime);
        track = ContextCompat.getColor(context, R.color.stroke);
        inner = ContextCompat.getColor(context, R.color.bg);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(track);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(lime);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(lime);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setColor(Color.WHITE);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(lime);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(inner);
    }

    public void setProgress(float progress) {
        float target = Math.max(0f, Math.min(1f, progress));
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(displayProgress, target);
        animator.setDuration(650L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            displayProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setProgressImmediate(float progress) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        float target = Math.max(0f, Math.min(1f, progress));
        if (Math.abs(target - displayProgress) < 0.0005f) {
            return;
        }
        displayProgress = target;
        invalidate();
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
        invalidate();
    }

    public void setRunning(boolean running) {
        this.running = running;
        if (running) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - dp(34f);
        if (radius <= 0f) {
            return;
        }

        float stroke = dp(11f);
        float tickOuter = radius + dp(20f);
        int tickCount = 60;
        for (int i = 0; i < tickCount; i++) {
            double angle = Math.toRadians(-90 + i * (360.0 / tickCount));
            boolean major = i % 5 == 0;
            float start = tickOuter - (major ? dp(7f) : dp(3.5f));
            float end = tickOuter;
            tickPaint.setStrokeWidth(major ? dp(1.4f) : dp(1f));
            tickPaint.setAlpha(major ? 90 : 38);
            canvas.drawLine(
                    cx + (float) Math.cos(angle) * start,
                    cy + (float) Math.sin(angle) * start,
                    cx + (float) Math.cos(angle) * end,
                    cy + (float) Math.sin(angle) * end,
                    tickPaint);
        }

        canvas.drawCircle(cx, cy, radius - dp(18f), fillPaint);

        trackPaint.setStrokeWidth(stroke);
        trackPaint.setAlpha(armed ? 150 : 90);
        canvas.drawCircle(cx, cy, radius, trackPaint);

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        float sweep = 360f * displayProgress;

        if (sweep > 0.4f) {
            float pulse = 0.5f;
            if (running) {
                pulse = (float) ((Math.sin(SystemClock.uptimeMillis() / 480.0) + 1.0) / 2.0);
            }
            int baseAlpha = armed ? 255 : 92;

            glowPaint.setStrokeWidth(stroke + dp(22f));
            glowPaint.setAlpha((int) ((armed ? 10 : 5) + (running ? 10 * pulse : 0)));
            canvas.drawArc(arcRect, -90f, sweep, false, glowPaint);

            glowPaint.setStrokeWidth(stroke + dp(13f));
            glowPaint.setAlpha((int) ((armed ? 18 : 8) + (running ? 14 * pulse : 0)));
            canvas.drawArc(arcRect, -90f, sweep, false, glowPaint);

            progressPaint.setStrokeWidth(stroke);
            progressPaint.setAlpha(baseAlpha);
            canvas.drawArc(arcRect, -90f, sweep, false, progressPaint);

            double endAngle = Math.toRadians(-90f + sweep);
            float dotX = cx + (float) Math.cos(endAngle) * radius;
            float dotY = cy + (float) Math.sin(endAngle) * radius;

            dotPaint.setAlpha(armed ? 45 : 20);
            canvas.drawCircle(dotX, dotY, dp(14f) + (running ? dp(3f) * pulse : 0f), dotPaint);

            dotPaint.setAlpha(baseAlpha);
            canvas.drawCircle(dotX, dotY, dp(5.5f), dotPaint);
        }

        if (running) {
            postInvalidateOnAnimation();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }
}
