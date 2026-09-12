package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

import org.junit.Test;

/** Regression coverage for reusing the already-required final History Live verify as a time anchor. */
public final class HistorySyncFinalTimeAnchorRegressionTest {
    @Test public void everyHistoryFamilyAttemptPersistsOnlyTheFinalVerifiedDefaultRead() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        String wire = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/MonthlyArchiveNfcWire.java");

        int perform = activity.indexOf("private FamilyAttempt performFamilyAttempt(");
        int finalVerifyGuard = activity.indexOf("if (transport.finalRestoreVerified())", perform);
        int persist = activity.indexOf("LiveTimeAnchorPersistence.persist(", finalVerifyGuard);
        int capturedAnchor = activity.indexOf("wire.lastDefaultTimeAnchor(expectedMeter)", persist);
        int recordAttempt = activity.indexOf("recordFamilyAttempt(", capturedAnchor);

        assertTrue(perform >= 0);
        assertTrue(finalVerifyGuard > perform);
        assertTrue(persist > finalVerifyGuard);
        assertTrue(capturedAnchor > persist);
        assertTrue(recordAttempt > capturedAnchor);

        // Both an individual family and every member of Sync All use the same common attempt path.
        int single = activity.indexOf("FamilyAttempt attempt = performFamilyAttempt(");
        int allLoop = activity.indexOf("for (int familyIndex = 0; familyIndex < SYNC_ALL_ORDER.length; familyIndex++)");
        int allAttempt = activity.indexOf("attempt = performFamilyAttempt(", allLoop);
        assertTrue(single >= 0);
        assertTrue(allLoop >= 0);
        assertTrue(allAttempt > allLoop);

        // Capturing the acquisition window must not add another NFC/default read.
        assertTrue(wire.contains("long readBeforeEpochMs = System.currentTimeMillis();"));
        assertTrue(wire.contains(".readAssumingDefaultApplication();"));
        assertTrue(wire.contains("long readAfterEpochMs = System.currentTimeMillis();"));
        assertTrue(wire.contains("VerifiedLiveTimeAnchor lastDefaultTimeAnchor(String expectedMeterId)"));
        assertTrue(wire.contains("LiveTimeAnchorPersistence.candidate("));
    }

    @Test public void postSyncAnchorResolvesArchiveThatIsNewerThanThePreviousLiveAnchorForAllFamilies() {
        long oldAnchorEpoch = Instant.parse("2026-09-10T11:23:00Z").toEpochMilli();
        long newAnchorEpoch = Instant.parse("2026-09-10T15:50:00Z").toEpochMilli();
        MeterTimeModelStore.AnchorRecord oldAnchor = anchor(
                1L, oldAnchorEpoch, "2026-09-10 12:23", 100_000L);
        MeterTimeModelStore.AnchorRecord postSyncAnchor = anchor(
                2L, newAnchorEpoch, "2026-09-10 16:50", 116_020L);

        for (ArchiveFamilyPeriod.Family family : Arrays.asList(
                ArchiveFamilyPeriod.Family.HOUR,
                ArchiveFamilyPeriod.Family.DAY,
                ArchiveFamilyPeriod.Family.MONTH)) {
            ArchiveFamilyStore.StoredPeriod archive = period(
                    family,
                    "2026-09-10 15:00",
                    109_420L);

            // Before the final post-family Live/default verify, this archive is newer than the one
            // active anchor. The single-anchor model must fail closed rather than search for a
            // different historical anchor.
            ArchiveUtcProjection.Boundary before = ArchiveUtcProjection.resolveBoundary(
                    archive, Collections.singletonList(oldAnchor));
            assertEquals(ArchiveUtcProjection.BoundaryStatus.RESOLUTION_FAILED, before.status);
            assertEquals(MeterTimeResolver.Status.ON_TIME_AFTER_ANCHOR, before.resolverStatus);
            assertEquals(Long.valueOf(1L), before.anchorId);

            // The already-required final verify then becomes the new active anchor and resolves the
            // same archive occurrence normally.
            ArchiveUtcProjection.Boundary after = ArchiveUtcProjection.resolveBoundary(
                    archive, Arrays.asList(oldAnchor, postSyncAnchor));
            assertTrue(after.resolved());
            assertEquals(Long.valueOf(2L), after.anchorId);
            assertEquals(Long.valueOf(newAnchorEpoch - ((116_020L - 109_420L) * 1000L)), after.utcMs);
        }
    }

    private static MeterTimeModelStore.AnchorRecord anchor(
            long id, long epochMs, String raw, long onTime) {
        return new MeterTimeModelStore.AnchorRecord(
                id,
                "M1",
                epochMs - 100L,
                epochMs + 100L,
                epochMs,
                100L,
                raw,
                "04 6D 00 00",
                false,
                false,
                onTime,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
    }

    private static ArchiveFamilyStore.StoredPeriod period(
            ArchiveFamilyPeriod.Family family,
            String raw,
            long onTime) {
        return new ArchiveFamilyStore.StoredPeriod(
                7L,
                "M1",
                family,
                raw,
                "OT:" + onTime,
                ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL,
                onTime,
                "04 6D 00 00",
                0,
                0,
                "2026-09-10T16:45:00Z",
                1L,
                "2026-09-10T16:45:00Z",
                1L,
                MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE,
                MonthlyArchivePeriod.VALIDATION_COMPLETE,
                "structure",
                "content",
                1,
                0,
                0,
                0,
                0,
                0,
                "content",
                "structure",
                MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE,
                MonthlyArchivePeriod.VALIDATION_COMPLETE,
                "1.000 m3",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                onTime + " s",
                null,
                new LinkedHashMap<>());
    }

    private static String read(String path) throws Exception {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) {
            return new String(Files.readAllBytes(fromRoot), StandardCharsets.UTF_8);
        }
        Path fromModule = Paths.get(path.replaceFirst("^app/", ""));
        if (Files.exists(fromModule)) {
            return new String(Files.readAllBytes(fromModule), StandardCharsets.UTF_8);
        }
        throw new AssertionError("file not found: " + path + " from " + Paths.get("").toAbsolutePath());
    }
}
