package dev.tlamutant.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArchiveReproducibilityTest {
    @Test
    void configuresEveryArchiveForReproducibleBytes() throws IOException {
        String build = Files.readString(Path.of("build.gradle.kts"));

        assertTrue(build.contains("tasks.withType<AbstractArchiveTask>().configureEach"));
        assertTrue(build.contains("isPreserveFileTimestamps = false"));
        assertTrue(build.contains("isReproducibleFileOrder = true"));
        assertTrue(build.contains("filePermissions { unix(\"0644\") }"));
        assertTrue(build.contains("dirPermissions { unix(\"0755\") }"));
        assertTrue(build.contains("val verifyFrozenApplication by tasks.registering"));
        assertTrue(build.contains("dependsOn(integrationTest, \"verifyFrozenApplication\")"));
    }
}
