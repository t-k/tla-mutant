package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tlamutant.tlc.TlcRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExperimentIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void producesWitnessBearingGapCandidatesAcrossAllThreeFamilies() {
        TlcRunner runner = TlcRunner.forCurrentJava(
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")));
        ExperimentRunner experiment = new ExperimentRunner(runner, Duration.ofSeconds(30));

        ExperimentReport report = experiment.run(
                Path.of("src/testFixtures/resources/tla/mutations"),
                "MutationFixture.tla",
                "MutationFixture.cfg",
                temporaryDirectory);

        assertEquals(7, report.generatedMutants());
        assertEquals(7, report.evaluatedMutants());
        assertTrue(report.gapCandidates() >= 1);
        assertTrue(report.results().stream()
                .filter(result -> result.verdict().equals("state-invariant-gap-candidate"))
                .allMatch(result -> result.mutantOnlyWitness() != null));
        assertEquals(
                java.util.Set.of("GCD", "BRL", "UOI"),
                report.results().stream().map(MutationResult::family).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void evaluatesMutantsFromReadOnlySnapshottedInputs() throws Exception {
        Path input = Files.createDirectory(temporaryDirectory.resolve("input"));
        try (var files = Files.list(Path.of("src/testFixtures/resources/tla/mutations"))) {
            for (Path source : files.toList()) {
                Path destination = input.resolve(source.getFileName().toString());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                Files.setPosixFilePermissions(destination, Set.of(PosixFilePermission.OWNER_READ));
            }
        }
        TlcRunner runner = TlcRunner.forCurrentJava(
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")));

        ExperimentReport report = new ExperimentRunner(runner, Duration.ofSeconds(30))
                .run(input, "MutationFixture.tla", "MutationFixture.cfg", temporaryDirectory.resolve("results"));

        assertEquals(7, report.generatedMutants());
        assertEquals(7, report.evaluatedMutants());
    }
}
