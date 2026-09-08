package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Source-level product contract for recovery from an interrupted archive application.
 *
 * <p>Physical W1 evidence showed that RF removal/recontact can leave the meter exposing the
 * selected archive application after a failed restore. A normal Live read must therefore restore
 * the default application before the protected M-Bus reset/data request. The archive safety shell
 * already performs that reset explicitly and must not duplicate it inside its verifier.</p>
 */
public final class NormalLiveDefaultResetContractTest {
    @Test public void normalLiveNormalizesApplicationBeforeProtectedRead() throws Exception {
        String reader = read(
                "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/QalcosonicReader.java");

        assertTrue(reader.contains(
                "Readout read() throws IOException {\n        return readInternal(true);\n    }"));
        assertTrue(reader.contains(
                "Readout readAssumingDefaultApplication() throws IOException {\n"
                        + "        return readInternal(false);\n    }"));

        int resetDefault = reader.indexOf(
                "issueMeterFrame(MbusFrameSupport.applicationResetDefault())");
        int stabilize = reader.indexOf(
                "SystemClock.sleep(APPLICATION_RESET_STABILIZATION_MS)", resetDefault);
        int mbusReset = reader.indexOf("issueMeterCommand(RESET_APP)", stabilize);
        int getData = reader.indexOf("issueMeterCommand(GET_DATA_1)", mbusReset);

        assertTrue(resetDefault >= 0);
        assertTrue(stabilize > resetDefault);
        assertTrue(mbusReset > stabilize);
        assertTrue(getData > mbusReset);
    }

    @Test public void archiveVerifierKeepsSingleExplicitSafetyShellReset() throws Exception {
        String wire = read(
                "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/MonthlyArchiveNfcWire.java");
        String shell = read(
                "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ArchiveFamilyTransportAdapter.java");

        assertTrue(wire.contains(".readAssumingDefaultApplication()"));
        assertTrue(shell.contains(
                "issueApplicationReset(\n                    wire, preflight, \"ARCHIVE_SYNC_START_RESET_DEFAULT\")"));
        assertTrue(shell.contains(
                "issueApplicationReset(\n                wire, result, \"ARCHIVE_SYNC_FINAL_RESET_DEFAULT\")"));
    }

    @Test public void dashboardStillUsesNormalReaderEntryPoint() throws Exception {
        String dashboard = read(
                "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        assertTrue(dashboard.contains(
                "new QalcosonicReader(nfcv, tag.getId()).read()"));
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
