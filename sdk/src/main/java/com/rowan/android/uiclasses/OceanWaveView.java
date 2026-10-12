package com.rowan.android.uiclasses;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;


import java.util.Locale;

/**
 * OceanWaveView
 * Rounded card with a live animated ocean wave, an OUTER border, a bordered icon circle
 * inside the water, and a TextView-like label above the wave (maxLines, ellipsize,
 * alignment, line spacing, letter spacing, font family, uppercase...).
 * <p>
 * <com.rowan.android.uiclasses.OceanWaveView
 * android:id="@+id/oceanWave"
 * android:layout_width="wrap_content"
 * android:layout_height="250dp"
 * android:layout_marginTop="10dp"
 * app:owv_waveColor="#216838"
 * app:owv_waveLevel=".35"
 * app:owv_waveSpeed=".4"
 * app:owv_cornerRadius="10dp"
 * app:owv_containerColor="#282828"
 * app:owv_outerBorderColor="#FFFFFF"
 * app:owv_outerBorderWidth="5dp"
 * app:owv_icon="@drawable/baseline_celebration_24"
 * app:owv_iconTint="#34A7FF"
 * app:owv_iconSize="30dp"
 * app:owv_iconCircleSize="60dp"
 * app:owv_iconBorderColor="#14FFE9"
 * app:owv_iconBorderWidth="2dp"
 * app:owv_text="Ocean Wave View"
 * app:owv_textColor="#ffffff"
 * app:owv_textSize="18sp"
 * app:owv_textFontStyle="italic"
 * app:owv_textMaxLines="2"
 * app:owv_textEllipsize="end"
 * app:owv_textAlign="left"
 * app:owv_textLineSpacingExtra="6dp"
 * app:owv_textLineSpacingMultiplier=".7"
 * app:owv_textLetterSpacing=".2"
 * app:owv_textUppercase="true"
 * app:owv_textPaddingTop="10dp"
 * app:owv_textPaddingHorizontal="10dp"
 * app:owv_textPosition="aboveWave"
 * app:layout_constraintEnd_toEndOf="parent"
 * app:layout_constraintStart_toStartOf="parent"
 * app:layout_constraintTop_toBottomOf="@id/oceanWaveTv" />
 */
public class OceanWaveView extends View {

    public static final int POSITION_TOP = 0;        // text sits textPaddingTop from the top edge
    public static final int POSITION_ABOVE_WAVE = 1; // text centered in the free space above the wave

    public static final int ALIGN_LEFT = 0, ALIGN_CENTER = 1, ALIGN_RIGHT = 2;

    // ---- config ----
    private int waveColor = Color.parseColor("#3DB67F");
    private int containerColor = Color.TRANSPARENT;
    private float waveLevel = 0.35f;
    private float waveAmplitude;
    private float waveSpeed = 1f;
    private float cornerRadius;

    private int outerBorderColor = Color.WHITE;
    private float outerBorderWidth = 0f;

    private Drawable icon;
    private int iconTint = Color.WHITE;
    private float iconSize;
    private float iconCircleSize;
    private int iconBorderColor = Color.WHITE;
    private float iconBorderWidth;

    private String text = "";
    private int textColor = Color.WHITE;
    private float textSize;
    private Typeface baseTypeface = Typeface.DEFAULT;
    private int textFontStyle = Typeface.BOLD;
    private int textMaxLines = 1;
    private TextUtils.TruncateAt textEllipsize = TextUtils.TruncateAt.END;
    private int textAlign = ALIGN_CENTER;
    private float textLineSpacingExtra = 0f;
    private float textLineSpacingMultiplier = 1f;
    private float textLetterSpacing = 0f;
    private boolean textUppercase = false;
    private float textPaddingTop;
    private float textPaddingHorizontal;
    private int textPosition = POSITION_TOP;

    // ---- drawing objects ----
    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaintBack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final Path clipPath = new Path();
    private final Path frontWave = new Path();
    private final Path backWave = new Path();
    private final Path arrowPath = new Path();
    private final RectF rect = new RectF();
    private final RectF borderRect = new RectF();

    private StaticLayout textLayout;

    // ---- animation ----
    private boolean running = true;
    private long lastNanos = 0L;
    private float phaseFront = 0f;
    private float phaseBack = 0f;
    private float displayedLevel;
    private ValueAnimator levelAnimator;
    private final float density;

    public OceanWaveView(Context c) {
        this(c, null);
    }

    public OceanWaveView(Context c, AttributeSet a) {
        this(c, a, 0);
    }

    public OceanWaveView(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        density = c.getResources().getDisplayMetrics().density;

        waveAmplitude = 8 * density;
        cornerRadius = 60 * density;
        iconSize = 22 * density;
        iconCircleSize = 52 * density;
        iconBorderWidth = 1.5f * density;
        textSize = 14 * density;
        textPaddingTop = 5 * density;
        textPaddingHorizontal = 12 * density;

        if (a != null) {
            TypedArray t = c.obtainStyledAttributes(a, R.styleable.OceanWaveView, defStyle, 0);
            try {
                waveColor = t.getColor(R.styleable.OceanWaveView_owv_waveColor, waveColor);
                waveLevel = t.getFloat(R.styleable.OceanWaveView_owv_waveLevel, waveLevel);
                waveAmplitude = t.getDimension(R.styleable.OceanWaveView_owv_waveAmplitude, waveAmplitude);
                waveSpeed = t.getFloat(R.styleable.OceanWaveView_owv_waveSpeed, waveSpeed);
                cornerRadius = t.getDimension(R.styleable.OceanWaveView_owv_cornerRadius, cornerRadius);
                containerColor = t.getColor(R.styleable.OceanWaveView_owv_containerColor, containerColor);
                outerBorderColor = t.getColor(R.styleable.OceanWaveView_owv_outerBorderColor, outerBorderColor);
                outerBorderWidth = t.getDimension(R.styleable.OceanWaveView_owv_outerBorderWidth, outerBorderWidth);

                icon = t.getDrawable(R.styleable.OceanWaveView_owv_icon);
                iconTint = t.getColor(R.styleable.OceanWaveView_owv_iconTint, iconTint);
                iconSize = t.getDimension(R.styleable.OceanWaveView_owv_iconSize, iconSize);
                iconCircleSize = t.getDimension(R.styleable.OceanWaveView_owv_iconCircleSize, iconCircleSize);
                iconBorderColor = t.getColor(R.styleable.OceanWaveView_owv_iconBorderColor, iconBorderColor);
                iconBorderWidth = t.getDimension(R.styleable.OceanWaveView_owv_iconBorderWidth, iconBorderWidth);

                String s = t.getString(R.styleable.OceanWaveView_owv_text);
                if (s != null) text = s;
                textColor = t.getColor(R.styleable.OceanWaveView_owv_textColor, textColor);
                textSize = t.getDimension(R.styleable.OceanWaveView_owv_textSize, textSize);
                textFontStyle = t.getInt(R.styleable.OceanWaveView_owv_textFontStyle, textFontStyle);
                textMaxLines = t.getInt(R.styleable.OceanWaveView_owv_textMaxLines, textMaxLines);
                textEllipsize = ellipsizeFromInt(t.getInt(R.styleable.OceanWaveView_owv_textEllipsize, 3));
                textAlign = t.getInt(R.styleable.OceanWaveView_owv_textAlign, textAlign);
                textLineSpacingExtra = t.getDimension(R.styleable.OceanWaveView_owv_textLineSpacingExtra, textLineSpacingExtra);
                textLineSpacingMultiplier = t.getFloat(R.styleable.OceanWaveView_owv_textLineSpacingMultiplier, textLineSpacingMultiplier);
                textLetterSpacing = t.getFloat(R.styleable.OceanWaveView_owv_textLetterSpacing, textLetterSpacing);
                textUppercase = t.getBoolean(R.styleable.OceanWaveView_owv_textUppercase, textUppercase);
                textPaddingTop = t.getDimension(R.styleable.OceanWaveView_owv_textPaddingTop, textPaddingTop);
                textPaddingHorizontal = t.getDimension(R.styleable.OceanWaveView_owv_textPaddingHorizontal, textPaddingHorizontal);
                textPosition = t.getInt(R.styleable.OceanWaveView_owv_textPosition, textPosition);

                // font family: @font/xxx resource OR a family name like "sans-serif-medium"
                int fontRes = t.getResourceId(R.styleable.OceanWaveView_owv_textFontFamily, 0);
                if (fontRes != 0) {
                    try {
                        Typeface f = ResourcesCompat.getFont(c, fontRes);
                        if (f != null) baseTypeface = f;
                    } catch (Exception ignored) {
                    }
                } else {
                    String fam = t.getString(R.styleable.OceanWaveView_owv_textFontFamily);
                    if (fam != null) baseTypeface = Typeface.create(fam, Typeface.NORMAL);
                }
            } finally {
                t.recycle();
            }
        }

        displayedLevel = clamp01(waveLevel);
        if (icon != null) icon = icon.mutate();
        setClickable(true);
        applyPaints();
    }

    // ------------------------------------------------------------------
    // Public API (everything can also be set from XML)
    // ------------------------------------------------------------------

    // wave / container
    public void setWaveColor(int color) {
        waveColor = color;
        applyPaints();
        invalidate();
    }

    public void setContainerColor(int color) {
        containerColor = color;
        applyPaints();
        invalidate();
    }

    public void setWaveAmplitude(float px) {
        waveAmplitude = px;
        invalidate();
    }

    public void setWaveSpeed(float speed) {
        waveSpeed = speed;
    }

    public void setCornerRadius(float px) {
        cornerRadius = px;
        rebuildClip();
        invalidate();
    }

    // outer border
    public void setOuterBorderColor(int color) {
        outerBorderColor = color;
        applyPaints();
        invalidate();
    }

    public void setOuterBorderWidth(float px) {
        outerBorderWidth = px;
        applyPaints();
        rebuildClip();
        invalidate();
    }

    // icon
    public void setIcon(Drawable d) {
        icon = d == null ? null : d.mutate();
        invalidate();
    }

    public void setIconResource(int resId) {
        setIcon(ContextCompat.getDrawable(getContext(), resId));
    }

    public void setIconTint(int color) {
        iconTint = color;
        applyPaints();
        invalidate();
    }

    public void setIconBorderColor(int color) {
        iconBorderColor = color;
        applyPaints();
        invalidate();
    }

    public void setIconBorderWidth(float px) {
        iconBorderWidth = px;
        applyPaints();
        invalidate();
    }

    // text (TextView-like)
    public void setText(String s) {
        text = s == null ? "" : s;
        rebuildTextLayout();
        invalidate();
    }

    public void setTextColor(int color) {
        textColor = color;
        applyPaints();
        invalidate();
    }

    public void setTextSize(float px) {
        textSize = px;
        applyPaints();
        rebuildTextLayout();
        invalidate();
    }

    public void setTextMaxLines(int lines) {
        textMaxLines = lines;
        rebuildTextLayout();
        invalidate();
    }

    public void setTextEllipsize(TextUtils.TruncateAt where) {
        textEllipsize = where;
        rebuildTextLayout();
        invalidate();
    }

    public void setTextAlign(int align) {
        textAlign = align;
        rebuildTextLayout();
        invalidate();
    }

    public void setTextLineSpacing(float extraPx, float multiplier) {
        textLineSpacingExtra = extraPx;
        textLineSpacingMultiplier = multiplier;
        rebuildTextLayout();
        invalidate();
    }

    public void setTextLetterSpacing(float em) {
        textLetterSpacing = em;
        applyPaints();
        rebuildTextLayout();
        invalidate();
    }

    public void setTextUppercase(boolean upper) {
        textUppercase = upper;
        rebuildTextLayout();
        invalidate();
    }

    public void setTextPaddingTop(float px) {
        textPaddingTop = px;
        invalidate();
    }

    public void setTextPaddingHorizontal(float px) {
        textPaddingHorizontal = px;
        rebuildTextLayout();
        invalidate();
    }

    public void setTextPosition(int position) {
        textPosition = position;
        invalidate();
    }

    /**
     * Pass any Typeface (e.g. Typeface.createFromAsset(...)).
     */
    public void setTextTypeface(Typeface tf) {
        baseTypeface = tf == null ? Typeface.DEFAULT : tf;
        applyPaints();
        rebuildTextLayout();
        invalidate();
    }

    public void setTextFontResource(int fontResId) {
        setTextTypeface(ResourcesCompat.getFont(getContext(), fontResId));
    }

    public void setTextFontFamily(String family) {
        setTextTypeface(family == null ? null : Typeface.create(family, Typeface.NORMAL));
    }

    /**
     * Typeface.NORMAL / BOLD / ITALIC / BOLD_ITALIC
     */
    public void setTextFontStyle(int style) {
        textFontStyle = style;
        applyPaints();
        rebuildTextLayout();
        invalidate();
    }

    /**
     * Set water level 0..1, optionally animated.
     */
    public void setWaveLevel(float level, boolean animate) {
        level = clamp01(level);
        waveLevel = level;
        if (levelAnimator != null) levelAnimator.cancel();
        if (!animate) {
            displayedLevel = level;
            invalidate();
            return;
        }
        levelAnimator = ValueAnimator.ofFloat(displayedLevel, level);
        levelAnimator.setDuration(700);
        levelAnimator.setInterpolator(new DecelerateInterpolator());
        levelAnimator.addUpdateListener(a -> displayedLevel = (float) a.getAnimatedValue());
        levelAnimator.start();
    }

    public void setAnimating(boolean animate) {
        if (running == animate) return;
        running = animate;
        lastNanos = 0L;
        if (running) postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastNanos = 0L;
        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (levelAnimator != null) levelAnimator.cancel();
        lastNanos = 0L;
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        lastNanos = 0L;
        if (visibility == VISIBLE && running) postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(
                resolveSize((int) (160 * density), wSpec),
                resolveSize((int) (240 * density), hSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuildClip();
        rebuildTextLayout();
    }

    private void rebuildClip() {
        rect.set(0, 0, getWidth(), getHeight());
        float r = Math.min(cornerRadius, Math.min(getWidth(), getHeight()) / 2f);
        clipPath.reset();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
    }

    private void applyPaints() {
        containerPaint.setStyle(Paint.Style.FILL);
        containerPaint.setColor(containerColor);

        wavePaint.setStyle(Paint.Style.FILL);
        wavePaint.setColor(waveColor);

        wavePaintBack.setStyle(Paint.Style.FILL);
        wavePaintBack.setColor(waveColor);
        wavePaintBack.setAlpha(110);

        outerBorderPaint.setStyle(Paint.Style.STROKE);
        outerBorderPaint.setColor(outerBorderColor);
        outerBorderPaint.setStrokeWidth(outerBorderWidth);

        iconBorderPaint.setStyle(Paint.Style.STROKE);
        iconBorderPaint.setColor(iconBorderColor);
        iconBorderPaint.setStrokeWidth(iconBorderWidth);

        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setColor(iconTint);
        arrowPaint.setStrokeWidth(2f * density);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);

        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.create(baseTypeface, textFontStyle));
        if (Build.VERSION.SDK_INT >= 21) textPaint.setLetterSpacing(textLetterSpacing);
    }

    private static TextUtils.TruncateAt ellipsizeFromInt(int v) {
        switch (v) {
            case 1:
                return TextUtils.TruncateAt.START;
            case 2:
                return TextUtils.TruncateAt.MIDDLE;
            case 3:
                return TextUtils.TruncateAt.END;
            default:
                return null; // none
        }
    }

    /**
     * Builds a StaticLayout so the text behaves like a TextView.
     */
    @SuppressWarnings("deprecation")
    private void rebuildTextLayout() {
        int avail = (int) (getWidth() - 2 * textPaddingHorizontal - 2 * outerBorderWidth);
        if (avail <= 0 || text == null || text.isEmpty()) {
            textLayout = null;
            return;
        }

        CharSequence src = textUppercase ? text.toUpperCase(Locale.getDefault()) : text;
        int maxLines = textMaxLines > 0 ? textMaxLines : Integer.MAX_VALUE;

        Layout.Alignment align;
        if (textAlign == ALIGN_LEFT) align = Layout.Alignment.ALIGN_NORMAL;
        else if (textAlign == ALIGN_RIGHT) align = Layout.Alignment.ALIGN_OPPOSITE;
        else align = Layout.Alignment.ALIGN_CENTER;

        if (Build.VERSION.SDK_INT >= 23) {
            StaticLayout.Builder b = StaticLayout.Builder.obtain(src, 0, src.length(), textPaint, avail)
                    .setAlignment(align)
                    .setLineSpacing(textLineSpacingExtra, textLineSpacingMultiplier)
                    .setIncludePad(true)
                    .setMaxLines(maxLines);
            if (textEllipsize != null) b.setEllipsize(textEllipsize).setEllipsizedWidth(avail);
            textLayout = b.build();
        } else {
            // API < 23: maxLines is not supported by StaticLayout, ellipsize works for 1 line
            textLayout = new StaticLayout(src, 0, src.length(), textPaint, avail, align,
                    textLineSpacingMultiplier, textLineSpacingExtra, true,
                    textEllipsize, avail);
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = (lastNanos == 0L) ? 0.016f : Math.min((now - lastNanos) / 1_000_000_000f, 0.05f);
        lastNanos = now;

        phaseFront += dt * 2.2f * waveSpeed;
        phaseBack -= dt * 1.5f * waveSpeed;

        final int w = getWidth();
        final int h = getHeight();
        if (w == 0 || h == 0) return;

        canvas.save();
        canvas.clipPath(clipPath);

        if (Color.alpha(containerColor) > 0) {
            canvas.drawRect(0, 0, w, h, containerPaint);
        }

        float waterTop = h * (1f - displayedLevel);
        buildWave(backWave, w, h, waterTop, waveAmplitude * 1.1f, phaseBack, 0.8f);
        buildWave(frontWave, w, h, waterTop, waveAmplitude, phaseFront, 1.2f);
        canvas.drawPath(backWave, wavePaintBack);
        canvas.drawPath(frontWave, wavePaint);

        canvas.restore();

        drawOuterBorder(canvas, w, h);
        drawContent(canvas, w, h, waterTop);

        if (running && isShown()) postInvalidateOnAnimation();
    }

    private void drawOuterBorder(Canvas canvas, int w, int h) {
        if (outerBorderWidth <= 0f) return;
        float half = outerBorderWidth / 2f;
        borderRect.set(half, half, w - half, h - half);
        float r = Math.max(0f, Math.min(cornerRadius, Math.min(w, h) / 2f) - half);
        canvas.drawRoundRect(borderRect, r, r, outerBorderPaint);
    }

    private void buildWave(Path p, int w, int h, float baseY,
                           float amp, float phase, float cycles) {
        p.reset();
        final int steps = 48;
        float stepX = w / (float) steps;
        p.moveTo(0, h);
        for (int i = 0; i <= steps; i++) {
            float x = i * stepX;
            double a = (x / w) * Math.PI * 2.0 * cycles + phase;
            float y = baseY + amp * (float) (Math.sin(a) + 0.35 * Math.sin(2.3 * a + phase));
            p.lineTo(x, y);
        }
        p.lineTo(w, h);
        p.close();
    }

    private void drawContent(Canvas canvas, int w, int h, float waterTop) {
        float cx = w / 2f;

        // ---- 1) bordered icon circle, centered inside the water ----
        float areaTop = waterTop + waveAmplitude * 1.5f;
        float areaBottom = h - cornerRadius * 0.15f - outerBorderWidth;
        float circleTop = areaTop + ((areaBottom - areaTop) - iconCircleSize) / 2f;
        if (circleTop < areaTop) circleTop = areaTop;
        float circleCy = circleTop + iconCircleSize / 2f;

        canvas.drawCircle(cx, circleCy, (iconCircleSize - iconBorderWidth) / 2f, iconBorderPaint);

        if (icon != null) {
            icon.setTint(iconTint);
            int half = (int) (iconSize / 2f);
            icon.setBounds((int) cx - half, (int) circleCy - half, (int) cx + half, (int) circleCy + half);
            icon.draw(canvas);
        } else {
            float s = iconSize * 0.28f;
            arrowPath.reset();
            arrowPath.moveTo(cx - s * 0.6f, circleCy - s * 1.4f);
            arrowPath.lineTo(cx + s * 0.8f, circleCy);
            arrowPath.lineTo(cx - s * 0.6f, circleCy + s * 1.4f);
            canvas.drawPath(arrowPath, arrowPaint);
        }

        // ---- 2) TextView-like text, centered horizontally, ABOVE the wave ----
        if (textLayout != null) {
            float minTop = outerBorderWidth + textPaddingTop;
            float textH = textLayout.getHeight();

            float top = minTop;
            if (textPosition == POSITION_ABOVE_WAVE) {
                float free = (waterTop - waveAmplitude * 1.5f) - minTop;
                if (free > textH) top = minTop + (free - textH) / 2f;
            }

            canvas.save();
            canvas.translate(outerBorderWidth + textPaddingHorizontal, top);
            textLayout.draw(canvas);
            canvas.restore();
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
