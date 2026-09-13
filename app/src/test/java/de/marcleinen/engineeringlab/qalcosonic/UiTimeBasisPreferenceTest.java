package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class UiTimeBasisPreferenceTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void localIsTheDefault() {
        assertEquals(AppTimeBasis.LOCAL, UiPreferences.getTimeBasis(context));
    }

    @Test public void meterCanBeSelectedAndPersisted() {
        UiPreferences.setTimeBasis(context, AppTimeBasis.METER);
        assertEquals(AppTimeBasis.METER, UiPreferences.getTimeBasis(context));
    }

    @Test public void corruptPreferenceFailsClosedToLocal() {
        context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE)
                .edit().putString("time_basis", "INVALID").commit();
        assertEquals(AppTimeBasis.LOCAL, UiPreferences.getTimeBasis(context));
    }
}
