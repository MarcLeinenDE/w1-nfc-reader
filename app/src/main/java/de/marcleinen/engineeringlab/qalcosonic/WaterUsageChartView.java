package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.graphics.Canvas;
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

/** Compact custom chart that consumes Material theme roles and retains a textual accessible summary. */
final class WaterUsageChartView extends View {
    enum Mode { BARS, LINE }

    static final class Entry {
        final String label;
        final double value;
        final boolean partial;

        Entry(String label, double value, boolean partial) {
            this.label = label == null ? "" : label;
            this.value = Math.max(0.0, value);
            this.partial = partial;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Entry> entries = Collections.emptyList();
    private Mode mode = Mode.BARS;
    private String emptyText = "—";

    WaterUsageChartView(Context context) { super(context); init(); }
    WaterUsageChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setMinimumHeight(dp(235));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setData(List<Entry> values, Mode mode) {
        this.entries = values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
        this.mode = mode == null ? Mode.BARS : mode;
        invalidate();
    }

    void setEmptyText(String value) {
        emptyText = value == null ? "—" : value;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float fontScale = getResources().getConfiguration().fontScale;
        int desired = dp(245 + Math.round(Math.max(0f, fontScale - 1f) * 100f));
        int height = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int text = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        int muted = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int grid = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
        int primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary);
        int partial = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary);

        float left = dp(42), right = getWidth() - dp(10), top = dp(18), bottom = getHeight() - dp(44);
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

        double maxValue = 0.0;
        for (Entry entry : entries) maxValue = Math.max(maxValue, entry.value);
        if (maxValue <= 0.0) maxValue = 1.0;
        maxValue *= 1.12;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(grid);
        for (int i = 0; i <= 2; i++) {
            float y = top + (bottom - top) * i / 2f;
            canvas.drawLine(left, y, right, y, paint);
            double tick = maxValue * (2 - i) / 2.0;
            canvas.drawText(formatTick(tick), left - dp(6), y + dp(3), textPaint);
        }

        if (mode == Mode.LINE) drawLine(canvas, left, right, top, bottom, maxValue, primary, muted);
        else drawBars(canvas, left, right, top, bottom, maxValue, primary, partial, muted);
    }

    private void drawBars(Canvas canvas, float left, float right, float top, float bottom,
                          double maxValue, int primary, int partial, int muted) {
        float width = right - left;
        float slot = width / Math.max(1, entries.size());
        float barWidth = Math.max(dp(6), slot * 0.56f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(9));
        textPaint.setColor(muted);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float cx = left + slot * (i + 0.5f);
            float y = bottom - (float) (entry.value / maxValue) * (bottom - top);
            RectF rect = new RectF(cx - barWidth / 2f, y, cx + barWidth / 2f, bottom);
            paint.setColor(entry.partial ? partial : primary);
            paint.setStyle(entry.partial ? Paint.Style.STROKE : Paint.Style.FILL);
            paint.setStrokeWidth(entry.partial ? dp(2) : 0f);
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            canvas.drawText(entry.label, cx, bottom + dp(20), textPaint);
        }
    }

    private void drawLine(Canvas canvas, float left, float right, float top, float bottom,
                          double maxValue, int primary, int muted) {
        float width = right - left;
        float slot = entries.size() <= 1 ? 0f : width / (entries.size() - 1f);
        Path path = new Path();
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(9));
        textPaint.setColor(muted);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float x = entries.size() <= 1 ? (left + right) / 2f : left + slot * i;
            float y = bottom - (float) (entry.value / maxValue) * (bottom - top);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            canvas.drawText(entry.label, x, bottom + dp(20), textPaint);
        }
        paint.setColor(primary);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float x = entries.size() <= 1 ? (left + right) / 2f : left + slot * i;
            float y = bottom - (float) (entry.value / maxValue) * (bottom - top);
            canvas.drawCircle(x, y, dp(3), paint);
        }
    }

    private static String formatTick(double value) {
        if (value >= 100.0) return String.format(Locale.getDefault(), "%.0f", value);
        if (value >= 10.0) return String.format(Locale.getDefault(), "%.1f", value);
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private float sp(int value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
