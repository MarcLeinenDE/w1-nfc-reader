package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class MeterLifecycleStoreTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("meter_lifecycle_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void confirmedReplacementKeepsOneActiveMeterAndPredecessorChain() {
        MeterLifecycleStore store = new MeterLifecycleStore(context);
        store.adoptInitialMeter("A");
        store.confirmReplacement("A", "B", 2000L, 2000L, 0.125);
        store.confirmReplacement("B", "C", 3000L, 3000L, 0.050);

        assertEquals("C", store.activeMeterId());
        assertEquals(List.of("A", "B", "C"), store.chainMeterIds());
        assertEquals(2, store.transitions().size());
        assertEquals("A", store.transitions().get(0).predecessorMeterId);
        assertEquals("B", store.transitions().get(0).successorMeterId);
    }

    @Test public void replacementMustStartAtCurrentActiveMeter() {
        MeterLifecycleStore store = new MeterLifecycleStore(context);
        store.adoptInitialMeter("A");
        assertThrows(IllegalStateException.class,
                () -> store.confirmReplacement("X", "B", 2000L, 2000L, 0.1));
        assertEquals("A", store.activeMeterId());
    }

    @Test public void startFreshDropsPredecessorChain() {
        MeterLifecycleStore store = new MeterLifecycleStore(context);
        store.adoptInitialMeter("A");
        store.confirmReplacement("A", "B", 2000L, 2000L, 0.1);
        store.startFresh("C");

        assertEquals("C", store.activeMeterId());
        assertEquals(List.of("C"), store.chainMeterIds());
        assertEquals(0, store.transitions().size());
    }
}
