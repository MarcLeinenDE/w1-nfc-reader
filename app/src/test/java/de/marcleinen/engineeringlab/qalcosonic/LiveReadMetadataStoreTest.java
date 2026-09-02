package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class LiveReadMetadataStoreTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("latest_live_read_metadata", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void newerSuccessfulReadReplacesFreshnessEvenAtSameTotal() {
        LiveReadMetadataStore store = new LiveReadMetadataStore(context);
        MbusParser.MeterData first = meter("M1", 203.498, 88, 0L);
        MbusParser.MeterData second = meter("M1", 203.498, 87, 0L);

        store.record(first, 1000L);
        store.record(second, 2000L);

        LiveReadMetadataStore.Summary summary = store.get("M1");
        assertTrue(summary.available());
        assertEquals(2000L, summary.readAtMs);
        assertEquals(203.498, summary.totalM3, 0.000001);
        assertEquals(Integer.valueOf(87), summary.batteryPercent);
        assertFalse(summary.hasAlarms());
    }

    private static MbusParser.MeterData meter(String id, double total, int battery, long ignored) {
        MbusParser.MeterData data = new MbusParser.MeterData();
        data.meterId = id;
        data.waterUsageM3 = total;
        data.batteryPercent = battery;
        return data;
    }
}
