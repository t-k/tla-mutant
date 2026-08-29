package dev.tlamutant.sandbox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PodmanCommandTest {
    @Test
    void commandMakesEveryContainmentBoundaryExplicit() {
        ExecutionPolicy.Limits limits =
                new ExecutionPolicy.Limits(60, 536_870_912, 64, 128, 33_554_432, 2048, 1024, 1024, 10_000, 90);
        List<String> command = PodmanCommand.create(
                "tla-mutant-0123456789abcdef",
                "0123456789abcdef",
                "tla-mutant-scratch-0123456789abcdef",
                "docker.io/library/eclipse-temurin@sha256:" + "a".repeat(64),
                Path.of("/input"),
                Path.of("/app.jar"),
                Path.of("/tools.jar"),
                "Spec.tla",
                "Spec.cfg",
                501,
                20,
                limits);
        String joined = String.join(" ", command);

        assertTrue(joined.contains("--network none"));
        assertTrue(joined.contains("--label dev.tlamutant.run=0123456789abcdef"));
        assertTrue(joined.contains("--read-only"));
        assertTrue(joined.contains("--cap-drop ALL"));
        assertTrue(joined.contains("no-new-privileges"));
        assertTrue(joined.contains("seccomp=/usr/share/containers/seccomp.json"));
        assertTrue(joined.contains("--user 501:20"));
        assertTrue(joined.contains("--userns keep-id"));
        assertTrue(joined.contains("--pids-limit 64"));
        assertTrue(joined.contains("--memory 536870912 --memory-swap 536870912"));
        assertTrue(joined.contains("--ulimit cpu=60:60"));
        assertTrue(joined.contains("--ulimit fsize=33554432:33554432"));
        assertTrue(joined.contains("--max-states 10000"));
        assertTrue(joined.contains("--max-state-bytes 33554432"));
        assertTrue(joined.contains("--read-only-tmpfs=false"));
        assertTrue(joined.contains("-Djava.io.tmpdir=/scratch"));
        assertTrue(joined.contains("-cp /opt/app.jar:/opt/tla2tools.jar dev.tlamutant.cli.Main"));
        assertFalse(joined.contains("-jar /opt/app.jar"));
        assertTrue(joined.contains("type=volume,source=tla-mutant-scratch-0123456789abcdef,destination=/scratch"));
        assertFalse(joined.contains("volume-nocopy"));
        assertTrue(joined.contains("type=bind,source=/input,destination=/input,ro=true"));
        assertTrue(joined.contains("--pull never"));
        assertFalse(joined.contains("--privileged"));
        assertFalse(joined.contains("--device"));
        assertFalse(joined.contains("fsize=32768:32768"));
    }

    @Test
    void censusUsesTheSameBoundaryWithoutMutationOrStateOutputArguments() {
        ExecutionPolicy.Limits limits =
                new ExecutionPolicy.Limits(60, 536_870_912, 64, 128, 33_554_432, 2048, 1024, 1024, 10_000, 90);
        List<String> command = PodmanCommand.create(
                "tla-mutant-0123456789abcdef",
                "0123456789abcdef",
                "tla-mutant-scratch-0123456789abcdef",
                "docker.io/library/eclipse-temurin@sha256:" + "a".repeat(64),
                Path.of("/input"),
                Path.of("/app.jar"),
                Path.of("/tools.jar"),
                "Spec.tla",
                "Spec.cfg",
                501,
                20,
                limits,
                SandboxTask.CENSUS);
        String joined = String.join(" ", command);

        assertTrue(joined.contains("dev.tlamutant.cli.Main census --model-dir /input"));
        assertTrue(joined.contains("--module Spec.tla --config Spec.cfg"));
        assertTrue(joined.contains("--network none"));
        assertTrue(joined.contains("--read-only"));
        assertTrue(joined.contains("--cap-drop ALL"));
        assertTrue(joined.contains("dev.tlamutant.run=0123456789abcdef"));
        assertFalse(joined.contains("dev.tlamutant.cli.Main experiment"));
        assertFalse(joined.contains("--max-states"));
        assertFalse(joined.contains("--max-state-bytes"));
        assertFalse(joined.contains("--work-dir"));
    }

    @Test
    void experimentPassesOnlyExplicitStateInvariantNamesWithoutAShell() {
        ExecutionPolicy.Limits limits =
                new ExecutionPolicy.Limits(60, 536_870_912, 64, 128, 33_554_432, 2048, 1024, 1024, 10_000, 90);
        List<String> command = PodmanCommand.create(
                "tla-mutant-0123456789abcdef",
                "0123456789abcdef",
                "tla-mutant-scratch-0123456789abcdef",
                "docker.io/library/eclipse-temurin@sha256:" + "a".repeat(64),
                Path.of("/input"),
                Path.of("/app.jar"),
                Path.of("/tools.jar"),
                "Spec.tla",
                "Spec.cfg",
                501,
                20,
                limits,
                SandboxTask.EXPERIMENT,
                List.of("TypeOK", "Safety"));

        assertTrue(command.containsAll(List.of("--state-invariant", "TypeOK", "Safety")));
        assertTrue(String.join(" ", command).contains("--state-invariant TypeOK --state-invariant Safety"));
        assertFalse(command.contains("sh"));
        assertFalse(command.contains("bash"));
    }

    @Test
    void attributedExperimentPreservesThreeOracleSetsAndOneJsonEditArgument() {
        ExecutionPolicy.Limits limits =
                new ExecutionPolicy.Limits(60, 536_870_912, 64, 128, 33_554_432, 2048, 1024, 1024, 10_000, 90);
        CorpusManifest.DerivedSourceEdit edit = edit("NoOverlap == $() /\\ TRUE\n");
        SandboxInput input =
                SandboxInput.attributed(List.of("TypeOK"), List.of("Safety"), List.of("NoOverlap"), List.of(edit));

        List<String> command = PodmanCommand.create(
                "tla-mutant-0123456789abcdef",
                "0123456789abcdef",
                "tla-mutant-scratch-0123456789abcdef",
                "docker.io/library/eclipse-temurin@sha256:" + "a".repeat(64),
                Path.of("/input"),
                Path.of("/app.jar"),
                Path.of("/tools.jar"),
                "Spec.tla",
                "Spec.cfg",
                501,
                20,
                limits,
                SandboxTask.EXPERIMENT,
                input);

        assertTrue(command.containsAll(List.of(
                "--type-invariant",
                "TypeOK",
                "--legacy-target-invariant",
                "Safety",
                "--adopted-target-invariant",
                "NoOverlap")));
        int jsonOption = command.indexOf("--derived-source-edit-json");
        assertTrue(jsonOption > 0);
        String json = command.get(jsonOption + 1);
        assertTrue(json.contains("NoOverlap == $()"));
        assertFalse(command.contains("sh"));
        assertFalse(command.contains("bash"));
    }

    @Test
    void censusTransportsOnlyDerivedEditMetadata() {
        ExecutionPolicy.Limits limits =
                new ExecutionPolicy.Limits(60, 536_870_912, 64, 128, 33_554_432, 2048, 1024, 1024, 10_000, 90);

        List<String> command = PodmanCommand.create(
                "tla-mutant-0123456789abcdef",
                "0123456789abcdef",
                "tla-mutant-scratch-0123456789abcdef",
                "docker.io/library/eclipse-temurin@sha256:" + "a".repeat(64),
                Path.of("/input"),
                Path.of("/app.jar"),
                Path.of("/tools.jar"),
                "Spec.tla",
                "Spec.cfg",
                501,
                20,
                limits,
                SandboxTask.CENSUS,
                SandboxInput.census(List.of(edit("NoOverlap == TRUE\n"))));
        String joined = String.join(" ", command);

        assertTrue(joined.contains("census --model-dir /input"));
        assertTrue(joined.contains("--work-dir /scratch/work"));
        assertTrue(command.contains("--derived-source-edit-json"));
        assertFalse(joined.contains("--type-invariant"));
        assertFalse(joined.contains("--legacy-target-invariant"));
        assertFalse(joined.contains("--adopted-target-invariant"));
    }

    @Test
    void rejectsOverlappingCategorizedOraclesAndCensusInvariants() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SandboxInput.attributed(List.of("TypeOK"), List.of("TypeOK"), List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SandboxInput(List.of("Flat"), List.of("TypeOK"), List.of(), List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> PodmanCommand.create(
                        "tla-mutant-0123456789abcdef",
                        "0123456789abcdef",
                        "tla-mutant-scratch-0123456789abcdef",
                        "docker.io/library/eclipse-temurin@sha256:" + "a".repeat(64),
                        Path.of("/input"),
                        Path.of("/app.jar"),
                        Path.of("/tools.jar"),
                        "Spec.tla",
                        "Spec.cfg",
                        501,
                        20,
                        new ExecutionPolicy.Limits(60, 536_870_912, 64, 128, 33_554_432, 2048, 1024, 1024, 10_000, 90),
                        SandboxTask.CENSUS,
                        SandboxInput.legacy(List.of("Safety"))));
    }

    private static CorpusManifest.DerivedSourceEdit edit(String insertedText) {
        return new CorpusManifest.DerivedSourceEdit(
                "Spec.tla",
                "0".repeat(64),
                1,
                1,
                insertedText,
                "1".repeat(64),
                1 + insertedText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }
}
