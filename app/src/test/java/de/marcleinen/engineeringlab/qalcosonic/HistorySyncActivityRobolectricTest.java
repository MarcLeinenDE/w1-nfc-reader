package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.google.android.material.button.MaterialButton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class HistorySyncActivityRobolectricTest {
    private Application app;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        DataPortability.clearMeterData(app);
    }

    @After public void tearDown() {
        DataPortability.clearMeterData(app);
    }

    @Test public void startsWithoutMeterAndKeepsEveryActionDisabled() throws Exception {
        try (ActivityController<HistorySyncActivity> controller =
                     Robolectric.buildActivity(HistorySyncActivity.class).setup()) {
            HistorySyncActivity activity = controller.get();

            assertFalse(button(activity, "monthButton").isEnabled());
            assertFalse(button(activity, "dayButton").isEnabled());
            assertFalse(button(activity, "hourButton").isEnabled());
            assertFalse(button(activity, "allButton").isEnabled());
            assertFalse(button(activity, "fullResyncButton").isEnabled());
        }
    }

    @Test public void knownActiveMeterEnablesNormalSyncButNotFullResyncBeforeBaselines() throws Exception {
        new MeterLifecycleStore(app).adoptInitialMeter("12345678");

        try (ActivityController<HistorySyncActivity> controller =
                     Robolectric.buildActivity(HistorySyncActivity.class).setup()) {
            HistorySyncActivity activity = controller.get();

            assertTrue(button(activity, "monthButton").isEnabled());
            assertTrue(button(activity, "dayButton").isEnabled());
            assertTrue(button(activity, "hourButton").isEnabled());
            assertTrue(button(activity, "allButton").isEnabled());
            assertFalse(button(activity, "fullResyncButton").isEnabled());
        }
    }

    @Test public void completedFamilyStaysEnabledForIncrementalUpdateButFullResyncWaitsForAll()
            throws Exception {
        String meter = "12345678";
        new MeterLifecycleStore(app).adoptInitialMeter(meter);
        complete(meter, ArchiveFamilyPeriod.Family.DAY);

        try (ActivityController<HistorySyncActivity> controller =
                     Robolectric.buildActivity(HistorySyncActivity.class).setup()) {
            HistorySyncActivity activity = controller.get();

            assertTrue(button(activity, "monthButton").isEnabled());
            assertTrue(button(activity, "dayButton").isEnabled());
            assertTrue(button(activity, "hourButton").isEnabled());
            assertTrue(button(activity, "allButton").isEnabled());
            assertFalse(button(activity, "fullResyncButton").isEnabled());
            assertTrue(button(activity, "dayButton").getText().toString()
                    .contains(activity.getString(R.string.m3_sync_update_history)));
        }
    }

    @Test public void allCompleteBaselinesEnableNormalUpdatesAndExplicitFullResync()
            throws Exception {
        String meter = "12345678";
        new MeterLifecycleStore(app).adoptInitialMeter(meter);
        complete(meter, ArchiveFamilyPeriod.Family.HOUR);
        complete(meter, ArchiveFamilyPeriod.Family.DAY);
        complete(meter, ArchiveFamilyPeriod.Family.MONTH);

        try (ActivityController<HistorySyncActivity> controller =
                     Robolectric.buildActivity(HistorySyncActivity.class).setup()) {
            HistorySyncActivity activity = controller.get();

            assertTrue(button(activity, "monthButton").isEnabled());
            assertTrue(button(activity, "dayButton").isEnabled());
            assertTrue(button(activity, "hourButton").isEnabled());
            assertTrue(button(activity, "allButton").isEnabled());
            assertTrue(button(activity, "fullResyncButton").isEnabled());
            assertTrue(button(activity, "allButton").getText().toString()
                    .contains(activity.getString(R.string.v2_sync_all_update)));
            assertTrue(button(activity, "fullResyncButton").getText().toString()
                    .contains(activity.getString(R.string.v2_full_resync_action)));
            assertTrue(button(activity, "monthButton").getText().toString()
                    .contains(activity.getString(R.string.m3_sync_update_history)));
            assertTrue(button(activity, "dayButton").getText().toString()
                    .contains(activity.getString(R.string.m3_sync_update_history)));
            assertTrue(button(activity, "hourButton").getText().toString()
                    .contains(activity.getString(R.string.m3_sync_update_history)));
        }
    }

    private void complete(String meter, ArchiveFamilyPeriod.Family family) {
        new ArchiveFamilySyncStateStore(app).recordAttempt(
                meter,
                family,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                System.currentTimeMillis(),
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                true,
                "2026-01-01 00:00",
                "2026-09-06 00:00",
                200,
                200,
                0,
                0);
    }

    private static MaterialButton button(HistorySyncActivity activity, String fieldName)
            throws Exception {
        Field field = HistorySyncActivity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (MaterialButton) field.get(activity);
    }
}
