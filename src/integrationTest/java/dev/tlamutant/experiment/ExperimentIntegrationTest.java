package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tlamutant.tlc.TlcRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
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

    @Test
    void attributesOnlyKilledMutantsAcrossTypeLegacyAndAdoptedOracles() {
        TlcRunner runner = TlcRunner.forCurrentJava(
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")));
        Path results = temporaryDirectory.resolve("attribution-results");

        ExperimentReport report = new ExperimentRunner(runner, Duration.ofSeconds(30))
                .run(
                        Path.of("src/testFixtures/resources/tla/attribution"),
                        "AttributionFixture.tla",
                        "AttributionFixture.cfg",
                        results,
                        new InvariantOracleSets(List.of("TypeOK"), List.of("LegacyOK"), List.of("AdoptedOK")));

        assertEquals(2, report.schemaVersion());
        assertEquals(9, report.generatedMutants());
        assertEquals(9, report.evaluatedMutants());
        MutationResult target = result(report, "TargetStep");
        MutationResult type = result(report, "TypeStep");
        MutationResult pass = result(report, "PassStep");
        assertEquals("adopted-target-invariant-kill", target.verdict());
        assertEquals("PASSED", target.attribution().typeOnlyOracleOutcome());
        assertEquals("PASSED", target.attribution().legacyOracleOutcome());
        assertEquals("STATE_EXPANDING", target.attribution().legacyStateRelation());
        assertEquals("type-invariant-kill", type.verdict());
        assertEquals("KILLED_STATE_SAFETY", type.attribution().typeOnlyOracleOutcome());
        assertEquals("state-equal", pass.verdict());
        assertEquals(null, pass.attribution());

        assertTrue(Files.exists(results.resolve(target.id() + "-type-only-states.jsonl")));
        assertTrue(Files.exists(results.resolve(target.id() + "-legacy-states.jsonl")));
        assertTrue(Files.exists(results.resolve(type.id() + "-type-only-states.jsonl")));
        assertTrue(Files.notExists(results.resolve(type.id() + "-legacy-states.jsonl")));
        assertTrue(Files.notExists(results.resolve(pass.id() + "-type-only-states.jsonl")));
        assertTrue(Files.notExists(results.resolve(pass.id() + "-legacy-states.jsonl")));
    }

    private static MutationResult result(ExperimentReport report, String definition) {
        return report.results().stream()
                .filter(result ->
                        result.family().equals("GCD") && result.definition().equals(definition))
                .findFirst()
                .orElseThrow();
    }
}
