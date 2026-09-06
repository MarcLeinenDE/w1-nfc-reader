package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Keeps disposable development signing isolated from the stable installed application. */
public final class DebugBuildIdentityRegressionTest {
    @Test public void debugBuildUsesSideBySideApplicationIdSuffix() throws Exception {
        String build = new String(Files.readAllBytes(appBuildGradle()), StandardCharsets.UTF_8);
        assertTrue(build.contains("applicationId = \"de.marcleinen.w1nfcreader\""));
        assertTrue(build.contains("debug {"));
        assertTrue(build.contains("applicationIdSuffix = \".dev\""));
        assertTrue(build.contains("versionName = \"2.0.0-dev\""));
    }

    private static Path appBuildGradle() {
        Path fromRoot = Paths.get("app/build.gradle.kts");
        if (Files.exists(fromRoot)) return fromRoot;
        Path fromModule = Paths.get("build.gradle.kts");
        if (Files.exists(fromModule)) return fromModule;
        throw new AssertionError("app/build.gradle.kts not found from "
                + Paths.get("").toAbsolutePath());
    }
}
