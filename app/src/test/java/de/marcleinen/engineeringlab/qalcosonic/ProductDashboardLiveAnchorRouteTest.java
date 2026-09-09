package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProductDashboardLiveAnchorRouteTest {
    @Test public void normalLiveCapturesEpochWindowAroundTheExistingSingleRead() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        int before = activity.indexOf("long readBeforeEpochMs = System.currentTimeMillis();");
        int read = activity.indexOf("new QalcosonicReader(nfcv, tag.getId()).read();", before);
        int after = activity.indexOf("long readAfterEpochMs = System.currentTimeMillis();", read);
        int candidate = activity.indexOf("LiveTimeAnchorPersistence.candidate(", after);
        assertTrue(before >= 0 && read > before && after > read && candidate > after);
        assertTrue(activity.indexOf("new QalcosonicReader(nfcv, tag.getId()).read();", read + 1) < 0);
    }

    @Test public void foreignMeterCandidateRemainsMemoryOnlyUntilUserAcceptsReplacement() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        int mismatch = activity.indexOf("if (!active.equals(meter.meterId)) {");
        int mismatchEnd = activity.indexOf("return;", mismatch);
        String mismatchBlock = activity.substring(mismatch, mismatchEnd);
        assertTrue(mismatchBlock.contains("new PendingMeter(meter, now, active, timeAnchor)"));
        assertFalse(mismatchBlock.contains("persistLive("));

        int accept = activity.indexOf("private void acceptReplacement(PendingMeter candidate)");
        int acceptEnd = activity.indexOf("private void registerReplacementExport()", accept);
        assertTrue(activity.substring(accept, acceptEnd).contains(
                "persistLive(candidate.meter, candidate.readAtMs, candidate.timeAnchor)"));
    }

    @Test public void resetPathClearsV21TimeModelBeforePersistingAcceptedSuccessor() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        int startFresh = activity.indexOf("private void startFreshWith(PendingMeter candidate)");
        int end = activity.indexOf("private void buildUi()", startFresh);
        String block = activity.substring(startFresh, end);
        assertTrue(block.contains("DataPortabilityV3.clearMeterData(this)"));
        assertTrue(block.contains("persistLive(candidate.meter, candidate.readAtMs, candidate.timeAnchor)"));
        assertFalse(block.contains("DataPortability.clearMeterData(this)"));
    }

    private static String read(String relative) throws IOException {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..", relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
