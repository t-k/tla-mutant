package dev.tlamutant.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
                Duration.ofSeconds(180),
                List.of("Safety"));

        assertEquals(0, execution.exitCode(), execution.stderr());
        assertFalse(execution.timedOut());
        JsonNode report = new ObjectMapper().readTree(execution.stdout());
        assertEquals(7, report.required("generatedMutants").intValue());
        assertTrue(report.required("gapCandidates").intValue() >= 1);
        assertResourcesRemoved(execution);
    }

    @Test
    void runsOutcomeFreeCensusInsideTheSameBoundaryAndRemovesTheContainer() throws Exception {
        ExecutionPolicy.Limits limits = new ExecutionPolicy.Limits(
                120, 1_073_741_824, 128, 256, 67_108_864, 4096, 1_048_576, 1_048_576, 1_000_000, 180);
        PodmanRunner runner = new PodmanRunner(
                "docker.io/library/eclipse-temurin@sha256:cc0a57a8aa0730b543dc34e56ff6679609532a43f685f459863330d64e97fbb6",
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")),
                limits);

        PodmanExecution execution = runner.runCensus(
                Path.of("src/testFixtures/resources/tla/census"),
                "CensusFixture.tla",
                "CensusFixture.cfg",
                Duration.ofSeconds(60));

        assertEquals(0, execution.exitCode(), execution.stderr());
        assertFalse(execution.timedOut());
        JsonNode report = new ObjectMapper().readTree(execution.stdout());
        assertTrue(report.required("sites").isArray());
        assertTrue(report.required("sites").size() > 0);
        assertFalse(report.toString().contains("verdict"));
        assertFalse(report.toString().contains("stateRelation"));
        assertResourcesRemoved(execution);
    }

    @Test
    void removesTheExactContainerAndScratchVolumeAfterTimeout() throws Exception {
        ExecutionPolicy.Limits limits = new ExecutionPolicy.Limits(
                120, 1_073_741_824, 128, 256, 67_108_864, 4096, 1_048_576, 1_048_576, 1_000_000, 180);
        PodmanRunner runner = new PodmanRunner(
                "docker.io/library/eclipse-temurin@sha256:cc0a57a8aa0730b543dc34e56ff6679609532a43f685f459863330d64e97fbb6",
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")),
                limits);

        PodmanExecution execution = runner.runCensus(
                Path.of("src/testFixtures/resources/tla/census"),
                "CensusFixture.tla",
                "CensusFixture.cfg",
                Duration.ofMillis(1));

        assertTrue(execution.timedOut());
        assertResourcesRemoved(execution);
    }

    private static void assertResourcesRemoved(PodmanExecution execution) throws Exception {
        assertResourceAbsent("container", execution.containerId());
        assertResourceAbsent("volume", execution.scratchVolume());
    }

    private static void assertResourceAbsent(String kind, String identity) throws Exception {
        Process inspection = new ProcessBuilder("podman", kind, "exists", identity)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        assertTrue(inspection.waitFor(30, TimeUnit.SECONDS));
        assertEquals(1, inspection.exitValue());
    }
}
