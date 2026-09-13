package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Theme-aware chart for the v2 Statistics metrics. Supports negative values and line gaps. */
final class V2MetricChartView extends View {
    enum Mode { BARS, LINE }
    enum AxisLabelMode { HORIZONTAL, ROTATED, HIDDEN }

    static final class Entry {
        final String label;
        final double value;
        final boolean partial;
        final String segment;
        final boolean connectFromPrevious;

        Entry(String label, double value, boolean partial, String segment,
              boolean connectFromPrevious) {
            this.label = label == null ? "" : label;
            this.value = value;
            this.partial = partial;
            this.segment = segment == null ? "" : segment;
            this.connectFromPrevious = connectFromPrevious;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Entry> entries = Collections.emptyList();
    private Mode mode = Mode.BARS;
    private String emptyText = "—";
    private String unit = "";

    V2MetricChartView(Context context) { super(context); init(); }
    V2MetricChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setMinimumHeight(dp(245));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setData(List<Entry> values, Mode mode, String unit) {
        this.entries = values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
        this.mode = mode == null ? Mode.BARS : mode;
        this.unit = unit == null ? "" : unit;
        requestLayout();
        invalidate();
    }

    void setEmptyText(String value) {
        emptyText = value == null ? "—" : value;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float fontScale = getResources().getConfiguration().fontScale;
        int extraForLabels = 0;
        if (mode == Mode.BARS && !entries.isEmpty()) {
            textPaint.setTextSize(sp(9));
            float plotWidth = Math.max(1f, width - dp(62));
            AxisLabelMode labelMode = barAxisLabelMode(plotWidth);
            if (labelMode == AxisLabelMode.ROTATED) {
                extraForLabels = Math.max(0,
                        Math.round(barLabelReserve(labelMode) - dp(44)));
            }
        }
        int desired = dp(250 + Math.round(Math.max(0f, fontScale - 1f) * 100f))
                + extraForLabels;
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int text = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        int muted = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int grid = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
        int primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary);
        int secondary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary);

        float left = dp(52), right = getWidth() - dp(10), top = dp(18);
        float plotWidth = Math.max(1f, right - left);
        textPaint.setTextSize(sp(9));
        AxisLabelMode barLabelMode = mode == Mode.BARS
                ? barAxisLabelMode(plotWidth)
                : AxisLabelMode.HIDDEN;
        int lineLabelStep = mode == Mode.LINE ? lineLabelStep(plotWidth) : 0;
        float bottomReserve = mode == Mode.BARS
                ? barLabelReserve(barLabelMode)
                : (lineLabelStep > 0 ? dp(44) : dp(24));
        float bottom = getHeight() - bottomReserve;
        if (right <= left || bottom <= top) return;

        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setTextSize(sp(10));
        textPaint.setColor(muted);
        textPaint.setTextAlign(Paint.Align.RIGHT);

        if (entries.isEmpty()) {
            textPaint.setTextSize(sp(13));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(text);
            canvas.drawText(emptyText, getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        double min = entries.get(0).value;
        double max = min;
        for (Entry entry : entries) {
            min = Math.min(min, entry.value);
            max = Math.max(max, entry.value);
        }
        if (mode == Mode.BARS) {
            min = Math.min(0.0, min);
            max = Math.max(0.0, max);
        }
        if (Math.abs(max - min) < 1e-9) {
            double pad = Math.max(1.0, Math.abs(max) * 0.1);
            min -= pad;
            max += pad;
        } else if (mode == Mode.LINE) {
            double pad = (max - min) * 0.10;
            min -= pad;
            max += pad;
        } else if (max > 0.0) {
            max *= 1.10;
        }

        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(grid);
        for (int i = 0; i <= 2; i++) {
            float y = top + (bottom - top) * i / 2f;
            canvas.drawLine(left, y, right, y, paint);
            double tick = max - (max - min) * i / 2.0;
            canvas.drawText(formatTick(tick), left - dp(6), y + dp(3), textPaint);
        }

        if (mode == Mode.LINE) {
            drawLine(canvas, left, right, top, bottom, min, max, primary, muted, lineLabelStep);
        } else {
            drawBars(canvas, left, right, top, bottom, min, max, primary, secondary, muted,
                    barLabelMode);
        }
    }

    private void drawBars(Canvas canvas, float left, float right, float top, float bottom,
                          double min, double max, int primary, int secondary, int muted,
                          AxisLabelMode labelMode) {
        float width = right - left;
        float slot = width / Math.max(1, entries.size());
        float barWidth = Math.max(dp(4), Math.min(dp(28), slot * 0.62f));
        float zeroY = valueY(0.0, top, bottom, min, max);
        textPaint.setTextSize(sp(9));
        textPaint.setColor(muted);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float cx = left + slot * (i + 0.5f);
            float valueY = valueY(entry.value, top, bottom, min, max);
            RectF rect = new RectF(cx - barWidth / 2f, Math.min(valueY, zeroY),
                    cx + barWidth / 2f, Math.max(valueY, zeroY));

            paint.setColor(entry.partial ? secondary : primary);
            paint.setPathEffect(entry.partial
                    ? new DashPathEffect(new float[]{dp(5), dp(3)}, 0f)
                    : null);

            // A partial edge is a real measured archive interval, but it is not fully contained in
            // the selected civil window. Draw its full measured value as a dashed outline so the
            // physical sequence stays visible without implying that the value belongs completely to
            // the selected window. Genuine zero-consumption buckets get a small baseline marker so
            // zero is distinguishable from missing data.
            if (Math.abs(entry.value) < 1e-12) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                canvas.drawLine(cx - barWidth / 2f, zeroY, cx + barWidth / 2f, zeroY, paint);
            } else {
                paint.setStyle(entry.partial ? Paint.Style.STROKE : Paint.Style.FILL);
                paint.setStrokeWidth(entry.partial ? dp(2) : dp(1));
                canvas.drawRoundRect(rect, dp(3), dp(3), paint);
            }
            paint.setPathEffect(null);

            // Axis labels use one central policy for every bar metric: horizontal while the complete
            // set fits, then rotate the complete set together, and finally hide the complete set if
            // even rotated glyph rows would collide into an unreadable text block. Data/bars are
            // never dropped or aggregated merely to make labels fit.
            if (labelMode == AxisLabelMode.ROTATED) {
                canvas.save();
                float labelTop = bottom + dp(8);
                canvas.rotate(90f, cx, labelTop);
                textPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(entry.label, cx, labelTop + dp(3), textPaint);
                canvas.restore();
            } else if (labelMode == AxisLabelMode.HORIZONTAL) {
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(entry.label, cx, bottom + dp(20), textPaint);
            }
        }
    }

    private void drawLine(Canvas canvas, float left, float right, float top, float bottom,
                          double min, double max, int primary, int muted, int labelStep) {
        float width = right - left;
        float slot = entries.size() <= 1 ? 0f : width / (entries.size() - 1f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(9));
        textPaint.setColor(muted);

        Path path = new Path();
        boolean pathStarted = false;
        String previousSegment = null;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float x = entries.size() <= 1 ? (left + right) / 2f : left + slot * i;
            float y = valueY(entry.value, top, bottom, min, max);
            boolean connect = pathStarted && entry.connectFromPrevious
                    && previousSegment != null && previousSegment.equals(entry.segment);
            if (connect) path.lineTo(x, y); else path.moveTo(x, y);
            pathStarted = true;
            previousSegment = entry.segment;
            if (labelStep > 0 && showLabel(i, labelStep)) {
                canvas.drawText(entry.label, x, bottom + dp(20), textPaint);
            }
        }
        paint.setPathEffect(null);
        paint.setColor(primary);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float x = entries.size() <= 1 ? (left + right) / 2f : left + slot * i;
            float y = valueY(entry.value, top, bottom, min, max);
            canvas.drawCircle(x, y, dp(3), paint);
        }
    }

    AxisLabelMode barAxisLabelMode(float plotWidth) {
        if (entries.isEmpty()) return AxisLabelMode.HIDDEN;
        textPaint.setTextSize(sp(9));
        float safeWidth = Math.max(1f, plotWidth);
        float slot = safeWidth / Math.max(1, entries.size());
        float maxWidth = 0f;
        for (Entry entry : entries) {
            maxWidth = Math.max(maxWidth, textPaint.measureText(entry.label));
        }

        if (entries.size() == 1) {
            return maxWidth + dp(8) <= safeWidth
                    ? AxisLabelMode.HORIZONTAL
                    : AxisLabelMode.HIDDEN;
        }
        if (maxWidth + dp(8) <= slot) return AxisLabelMode.HORIZONTAL;

        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float rotatedThickness = metrics.descent - metrics.ascent;
        return rotatedThickness + dp(4) <= slot
                ? AxisLabelMode.ROTATED
                : AxisLabelMode.HIDDEN;
    }

    private float barLabelReserve(AxisLabelMode labelMode) {
        if (labelMode == AxisLabelMode.HIDDEN) return dp(24);
        if (labelMode == AxisLabelMode.HORIZONTAL) return dp(44);
        textPaint.setTextSize(sp(9));
        float maxWidth = 0f;
        for (Entry entry : entries) maxWidth = Math.max(maxWidth, textPaint.measureText(entry.label));
        return Math.min(dp(132), Math.max(dp(72), maxWidth + dp(18)));
    }

    /**
     * Returns the smallest readable thinning step for line labels, or 0 when even the sparsest
     * first/last label set collides. Unlike the old count-only rule this uses actual plot width,
     * rendered label widths and current font scaling.
     */
    int lineLabelStep(float plotWidth) {
        if (entries.isEmpty()) return 0;
        textPaint.setTextSize(sp(9));
        float safeWidth = Math.max(1f, plotWidth);
        if (entries.size() == 1) {
            return textPaint.measureText(entries.get(0).label) + dp(8) <= safeWidth ? 1 : 0;
        }
        for (int step = 1; step < entries.size(); step++) {
            if (lineLabelsFit(safeWidth, step)) return step;
        }
        return 0;
    }

    private boolean lineLabelsFit(float plotWidth, int step) {
        float slot = plotWidth / (entries.size() - 1f);
        float previousRight = Float.NEGATIVE_INFINITY;
        float minimumGap = dp(6);
        for (int i = 0; i < entries.size(); i++) {
            if (!showLabel(i, step)) continue;
            float x = slot * i;
            float halfWidth = textPaint.measureText(entries.get(i).label) / 2f;
            float currentLeft = x - halfWidth;
            if (currentLeft < previousRight + minimumGap) return false;
            previousRight = x + halfWidth;
        }
        return true;
    }

    private float valueY(double value, float top, float bottom, double min, double max) {
        double fraction = (value - min) / (max - min);
        return bottom - (float) fraction * (bottom - top);
    }

    private boolean showLabel(int index, int step) {
        return index == entries.size() - 1 || index % step == 0;
    }

    private String formatTick(double value) {
        String number;
        double absolute = Math.abs(value);
        if (absolute >= 100.0) number = String.format(Locale.getDefault(), "%.0f", value);
        else if (absolute >= 10.0) number = String.format(Locale.getDefault(), "%.1f", value);
        else number = String.format(Locale.getDefault(), "%.2f", value);
        return unit.isEmpty() ? number : number + " " + unit;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private float sp(int value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
