package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class DataPortabilityV3TimeBasisTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        DataPortabilityV3.clearMeterData(context);
        context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void schema3RoundTripRestoresGlobalTimeBasis() throws Exception {
        UiPreferences.setTimeBasis(context, AppTimeBasis.METER);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortabilityV3.writeBackup(context, out);

        UiPreferences.setTimeBasis(context, AppTimeBasis.LOCAL);
        DataPortabilityV3.restoreBackup(context, out.toByteArray());

        assertEquals(AppTimeBasis.METER, UiPreferences.getTimeBasis(context));
    }

    @Test public void legacySchema2RestoreDoesNotOverrideV21TimeBasis() throws Exception {
        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, legacy);

        UiPreferences.setTimeBasis(context, AppTimeBasis.METER);
        DataPortabilityV3.restoreBackup(context, legacy.toByteArray());

        assertEquals(AppTimeBasis.METER, UiPreferences.getTimeBasis(context));
    }
}
