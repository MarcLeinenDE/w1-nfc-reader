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

    @Test public void startsWithoutMeterAndKeepsEveryFamilyActionDisabled() throws Exception {
        try (ActivityController<HistorySyncActivity> controller =
                     Robolectric.buildActivity(HistorySyncActivity.class).setup()) {
            HistorySyncActivity activity = controller.get();

            assertFalse(button(activity, "monthButton").isEnabled());
            assertFalse(button(activity, "dayButton").isEnabled());
            assertFalse(button(activity, "hourButton").isEnabled());
            assertFalse(button(activity, "allButton").isEnabled());
        }
    }

    @Test public void knownActiveMeterEnablesOnlyFirstMonthBaseline() throws Exception {
        new MeterLifecycleStore(app).adoptInitialMeter("12345678");

        try (ActivityController<HistorySyncActivity> controller =
                     Robolectric.buildActivity(HistorySyncActivity.class).setup()) {
            HistorySyncActivity activity = controller.get();

            assertTrue(button(activity, "monthButton").isEnabled());
            assertFalse(button(activity, "dayButton").isEnabled());
            assertFalse(button(activity, "hourButton").isEnabled());
            assertFalse(button(activity, "allButton").isEnabled());
        }
    }

    private static MaterialButton button(HistorySyncActivity activity, String fieldName)
            throws Exception {
        Field field = HistorySyncActivity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (MaterialButton) field.get(activity);
    }
}
