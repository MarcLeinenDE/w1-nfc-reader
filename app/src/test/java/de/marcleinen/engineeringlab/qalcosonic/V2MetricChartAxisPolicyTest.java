package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class V2MetricChartAxisPolicyTest {
    @Test public void barsUseWholeSetHorizontalThenRotatedThenHidden() {
        V2MetricChartView view = view();
        view.setData(entries(4, "12 Sep"), V2MetricChartView.Mode.BARS, "m3");
        assertEquals(V2MetricChartView.AxisLabelMode.HORIZONTAL,
                view.barAxisLabelMode(10_000f));

        view.setData(entries(2,
                "A deliberately very long archive period label that cannot fit horizontally"),
                V2MetricChartView.Mode.BARS,
                "m3");
        boolean foundRotatedFit = false;
        for (int width = 1; width <= 10_000; width++) {
            if (view.barAxisLabelMode(width) == V2MetricChartView.AxisLabelMode.ROTATED) {
                foundRotatedFit = true;
                break;
            }
        }
        assertTrue(foundRotatedFit);

        view.setData(entries(48, "12 Sep 2026 23:00"), V2MetricChartView.Mode.BARS, "m3");
        assertEquals(V2MetricChartView.AxisLabelMode.HIDDEN,
                view.barAxisLabelMode(1f));
    }

    @Test public void lineLabelsThinByMeasuredFitAndEventuallyHide() {
        V2MetricChartView view = view();
        view.setData(entries(12, "12 Sep"), V2MetricChartView.Mode.LINE, "V");
        assertEquals(1, view.lineLabelStep(10_000f));

        view.setData(entries(48, "12 Sep 2026 23:00"), V2MetricChartView.Mode.LINE, "V");
        int thinned = view.lineLabelStep(1_500f);
        assertTrue(thinned >= 1);

        assertEquals(0, view.lineLabelStep(1f));
    }

    private static V2MetricChartView view() {
        Context context = RuntimeEnvironment.getApplication();
        return new V2MetricChartView(context);
    }

    private static List<V2MetricChartView.Entry> entries(int count, String label) {
        List<V2MetricChartView.Entry> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new V2MetricChartView.Entry(
                    label + " " + i,
                    i,
                    false,
                    "segment",
                    i > 0));
        }
        return result;
    }
}
