package com.rowan.android.uiclasses;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * NeuralOrbView
 * A living, "thinking" orb made of dotted contour lines that continuously
 * morph and rotate. Pure Java, no dependencies, no center text.
 *
 * XML:
 *   <com.rowan.android.uiclasses.NeuralOrbView
 *       android:id="@+id/orb"
 *       android:layout_width="240dp"
 *       android:layout_height="240dp" />
 *
 * Code:
 *   orb.setOrbColor(0xFF2EE6A6);
 *   orb.setThinking(true);   // faster + more turbulent
 *   orb.setThinking(false);  // calm idle
 */
public class NeuralOrbView extends View {

    // ---- Geometry density (tweak for look / performance) ----
    private static final int MERIDIANS = 44;   // number of vertical dotted lines
    private static final int POINTS = 46;      // dots along each line
    private static final int BUCKETS = 5;      // depth layers (back -> front)

    private final float[] baseX = new float[MERIDIANS * POINTS];
    private final float[] baseY = new float[MERIDIANS * POINTS];
    private final float[] baseZ = new float[MERIDIANS * POINTS];

    private final float[][] bucketPts = new float[BUCKETS][MERIDIANS * POINTS * 2];
    private final int[] bucketCount = new int[BUCKETS];
    private final Paint[] bucketPaint = new Paint[BUCKETS];
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int orbColor = Color.parseColor("#2EE6A6");
    private float density;

    // animation state
    private boolean running = true;
    private long lastNanos = 0L;
    private float time = 0f;
    private float rotation = 0f;

    // "thinking" smoothing
    private float speedTarget = 1f, speedCurrent = 1f;
    private float ampTarget = 0.16f, ampCurrent = 0.16f;

    public NeuralOrbView(Context c) { super(c); init(c); }
    public NeuralOrbView(Context c, AttributeSet a) { super(c, a); init(c); }
    public NeuralOrbView(Context c, AttributeSet a, int s) { super(c, a, s); init(c); }

    private void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;

        // Pre-compute unit-sphere points laid out as meridian lines
        int k = 0;
        for (int m = 0; m < MERIDIANS; m++) {
            double theta = (m / (double) MERIDIANS) * Math.PI * 2.0;
            for (int p = 0; p < POINTS; p++) {
                double phi = ((p + 0.5) / POINTS) * Math.PI; // 0..PI
                baseX[k] = (float) (Math.sin(phi) * Math.cos(theta));
                baseY[k] = (float) Math.cos(phi);
                baseZ[k] = (float) (Math.sin(phi) * Math.sin(theta));
                k++;
            }
        }

        for (int i = 0; i < BUCKETS; i++) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            bucketPaint[i] = p;
        }
        applyColor();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Main orb color (ARGB). */
    public void setOrbColor(int color) {
        orbColor = color;
        applyColor();
        rebuildGlow(getWidth(), getHeight());
        invalidate();
    }

    /** true = fast, turbulent "thinking"; false = slow calm idle. */
    public void setThinking(boolean thinking) {
        speedTarget = thinking ? 2.6f : 1f;
        ampTarget = thinking ? 0.26f : 0.16f;
    }

    /** Pause / resume animation manually. */
    public void setAnimating(boolean animate) {
        if (running == animate) return;
        running = animate;
        lastNanos = 0L;
        if (running) postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastNanos = 0L;
        if (running) postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lastNanos = 0L;
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        lastNanos = 0L;
        if (visibility == VISIBLE && running) postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int def = (int) (220 * density);
        setMeasuredDimension(resolveSize(def, wSpec), resolveSize(def, hSpec));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuildGlow(w, h);
    }

    private void rebuildGlow(int w, int h) {
        if (w == 0 || h == 0) return;
        float r = Math.min(w, h) * 0.5f;
        int glow = Color.argb(70, Color.red(orbColor), Color.green(orbColor), Color.blue(orbColor));
        glowPaint.setShader(new RadialGradient(w / 2f, h / 2f, r,
                new int[]{glow, Color.TRANSPARENT},
                new float[]{0.35f, 1f}, Shader.TileMode.CLAMP));
    }

    private void applyColor() {
        // back layers dim & tiny, front layers bright & bigger
        for (int i = 0; i < BUCKETS; i++) {
            float t = i / (float) (BUCKETS - 1);
            int alpha = (int) (40 + 215 * t);
            bucketPaint[i].setColor(Color.argb(alpha,
                    Color.red(orbColor), Color.green(orbColor), Color.blue(orbColor)));
            bucketPaint[i].setStrokeWidth((0.9f + 1.5f * t) * density);
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = (lastNanos == 0L) ? 0.016f : Math.min((now - lastNanos) / 1_000_000_000f, 0.05f);
        lastNanos = now;

        // ease toward thinking / idle state
        speedCurrent += (speedTarget - speedCurrent) * Math.min(1f, dt * 3f);
        ampCurrent += (ampTarget - ampCurrent) * Math.min(1f, dt * 3f);

        time += dt * speedCurrent;
        rotation += dt * 0.35f * speedCurrent;

        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float radius = Math.min(w, h) * 0.34f;

        canvas.drawCircle(cx, cy, Math.min(w, h) * 0.5f, glowPaint);

        float cosR = (float) Math.cos(rotation), sinR = (float) Math.sin(rotation);
        float tilt = 0.22f; // slight tilt so it feels 3D
        float cosT = (float) Math.cos(tilt), sinT = (float) Math.sin(tilt);

        for (int i = 0; i < BUCKETS; i++) bucketCount[i] = 0;

        final int n = MERIDIANS * POINTS;
        for (int i = 0; i < n; i++) {
            float x = baseX[i], y = baseY[i], z = baseZ[i];

            // organic "brain wave" deformation (layered travelling sines)
            float noise =
                    0.50f * (float) Math.sin(3.1f * x + 2.0f * y + time * 1.10f) +
                    0.30f * (float) Math.sin(4.2f * y - 3.0f * z - time * 0.90f) +
                    0.20f * (float) Math.sin(5.3f * z + 2.0f * x + time * 1.70f);

            // slow pulse + pinch at the top like the reference shape
            float pinch = 1f - 0.18f * (float) Math.exp(-Math.pow((y - 1f) * 3.2f, 2));
            float r = (1f + ampCurrent * noise) * pinch;

            float px = x * r, py = y * r, pz = z * r;

            // rotate around Y
            float rx = px * cosR + pz * sinR;
            float rz = -px * sinR + pz * cosR;
            // tilt around X
            float ry = py * cosT - rz * sinT;
            float rz2 = py * sinT + rz * cosT;

            float persp = 1f / (1f - rz2 * 0.18f);
            float sx = cx + rx * radius * persp;
            float sy = cy - ry * radius * persp;

            float depth = (rz2 + 1.3f) / 2.6f; // ~0..1
            int b = (int) (depth * BUCKETS);
            if (b < 0) b = 0; else if (b >= BUCKETS) b = BUCKETS - 1;

            int idx = bucketCount[b]++ * 2;
            bucketPts[b][idx] = sx;
            bucketPts[b][idx + 1] = sy;
        }

        for (int b = 0; b < BUCKETS; b++) {
            if (bucketCount[b] > 0) {
                canvas.drawPoints(bucketPts[b], 0, bucketCount[b] * 2, bucketPaint[b]);
            }
        }

        if (running && isShown()) postInvalidateOnAnimation();
    }
}
