package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Locks the physically validated Research restore ordering without coupling NFC hardware to CI. */
public final class ArchiveResearchRestoreOrderingTest {
    @Test public void finalResetHasResearchCooldownBeforeResetAndLiveStabilizationAfter() throws Exception {
        String source = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/ArchiveFamilyTransportAdapter.java"));
        int method = source.indexOf("private static SafetyVerification restoreAndVerify(");
        int end = source.indexOf("private static void verifyDefault(", method);
        assertTrue(method >= 0 && end > method);
        String restore = source.substring(method, end);

        int pre = restore.indexOf("wire.coolDown(RESTORE_PRE_RESET_COOLDOWN_MS)");
        int reset = restore.indexOf("ARCHIVE_SYNC_FINAL_RESET_DEFAULT");
        int post = restore.indexOf("wire.coolDown(STABILIZATION_MS)", reset);
        int live = restore.indexOf("verifyDefault(verifier, result, expectedMeterId, initial)", post);
        assertTrue(pre >= 0 && pre < reset);
        assertTrue(reset < post);
        assertTrue(post < live);

        assertTrue(source.contains("+ RESTORE_PRE_RESET_COOLDOWN_MS"));
        assertTrue(source.contains("static final int RESTORE_PRE_RESET_COOLDOWN_MS = 1000"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path projectFile(String relative) {
        Path module = Paths.get(relative);
        if (Files.exists(module)) return module;
        Path root = Paths.get("app").resolve(relative);
        if (Files.exists(root)) return root;
        throw new AssertionError(relative + " not found from " + Paths.get("").toAbsolutePath());
    }
}
