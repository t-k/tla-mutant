package dev.tlamutant.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class PodmanContainmentTest {
    @Test
    void runsTheExperimentWithoutNetworkOrWritableHostOutputAndRemovesTheContainer() throws Exception {
        ExecutionPolicy.Limits limits = new ExecutionPolicy.Limits(
                120, 1_073_741_824, 128, 256, 67_108_864, 4096, 1_048_576, 1_048_576, 1_000_000, 180);
        PodmanRunner runner = new PodmanRunner(
                "docker.io/library/eclipse-temurin@sha256:cc0a57a8aa0730b543dc34e56ff6679609532a43f685f459863330d64e97fbb6",
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")),
                limits);

        PodmanExecution execution = runner.run(
                Path.of("src/testFixtures/resources/tla/mutations"),
                "MutationFixture.tla",
                "MutationFixture.cfg",
                Duration.ofSeconds(180));

        assertEquals(0, execution.exitCode(), execution.stderr());
        assertFalse(execution.timedOut());
        JsonNode report = new ObjectMapper().readTree(execution.stdout());
        assertEquals(7, report.required("generatedMutants").intValue());
        assertTrue(report.required("gapCandidates").intValue() >= 1);
        Process inspection = new ProcessBuilder("podman", "container", "exists", execution.containerId()).start();
        assertEquals(1, inspection.waitFor());
    }
}
