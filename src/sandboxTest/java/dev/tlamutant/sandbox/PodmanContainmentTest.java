package dev.tlamutant.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
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

    @Test
    void appliesADigestPinnedEditOnlyInsideTheCensusSandbox() throws Exception {
        ExecutionPolicy.Limits limits = new ExecutionPolicy.Limits(
                120, 1_073_741_824, 128, 256, 67_108_864, 4096, 1_048_576, 1_048_576, 1_000_000, 180);
        PodmanRunner runner = new PodmanRunner(
                "docker.io/library/eclipse-temurin@sha256:cc0a57a8aa0730b543dc34e56ff6679609532a43f685f459863330d64e97fbb6",
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")),
                limits);
        Path model = Path.of("src/testFixtures/resources/tla/census/CensusFixture.tla");
        byte[] base = Files.readAllBytes(model);
        byte[] marker = "Guard == x < 2".getBytes(StandardCharsets.UTF_8);
        int offset = indexOf(base, marker) + marker.length;
        byte[] inserted = " /\\ x >= 0".getBytes(StandardCharsets.UTF_8);
        byte[] derived = insert(base, offset, inserted);
        CorpusManifest.DerivedSourceEdit edit = new CorpusManifest.DerivedSourceEdit(
                "CensusFixture.tla",
                "Guard",
                digest(base),
                base.length,
                offset,
                new String(inserted, StandardCharsets.UTF_8),
                digest(derived),
                derived.length);

        PodmanExecution execution = runner.runCensus(
                model.getParent(),
                model.getFileName().toString(),
                "CensusFixture.cfg",
                Duration.ofSeconds(60),
                List.of(edit));

        assertEquals(0, execution.exitCode(), execution.stderr());
        JsonNode report = new ObjectMapper().readTree(execution.stdout());
        assertTrue(report.required("sites").findValuesAsText("guard").contains("x >= 0"));
        assertEquals(digest(base), digest(Files.readAllBytes(model)));
        assertResourcesRemoved(execution);
    }

    private static int indexOf(byte[] source, byte[] target) {
        outer:
        for (int start = 0; start <= source.length - target.length; start++) {
            for (int index = 0; index < target.length; index++) {
                if (source[start + index] != target[index]) {
                    continue outer;
                }
            }
            return start;
        }
        throw new IllegalArgumentException("Fixture marker is missing");
    }

    private static byte[] insert(byte[] base, int offset, byte[] inserted) {
        byte[] derived = new byte[base.length + inserted.length];
        System.arraycopy(base, 0, derived, 0, offset);
        System.arraycopy(inserted, 0, derived, offset, inserted.length);
        System.arraycopy(base, offset, derived, offset + inserted.length, base.length - offset);
        return derived;
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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
