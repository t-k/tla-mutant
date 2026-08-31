package dev.tlamutant.experiment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tlamutant.security.BoundedFileSnapshot;
import dev.tlamutant.tlc.TlcRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FullPropertyExperimentIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void fullPropertyKillsAStateExpandingSurvivorFromTheLegacyOracle() throws Exception {
        Path modelDirectory = Path.of("src/testFixtures/resources/tla/full-property");
        Path module = modelDirectory.resolve("FullPropertyMutation.tla");
        Path config = modelDirectory.resolve("FullPropertyMutation.cfg");
        Path manifest = temporaryDirectory.resolve("manifest.json");
        Files.writeString(manifest, "{\"schemaVersion\":1,\"model\":\"FullPropertyMutation\"}\n", UTF_8);
        Path application = Path.of(System.getProperty("tla.mutant.test.appJar"));
        Path tools = Path.of(System.getProperty("tla.mutant.test.toolsJar"));
        TlcRunner tlc = TlcRunner.forCurrentJava(application, tools);
        ExperimentRunner runner = new ExperimentRunner(tlc, Duration.ofSeconds(30));

        ExperimentReport legacy = runner.run(
                modelDirectory,
                "FullPropertyMutation.tla",
                "FullPropertyMutation.cfg",
                temporaryDirectory.resolve("legacy"));
        ExperimentReport full = runner.runFullProperty(
                modelDirectory,
                "FullPropertyMutation.tla",
                "FullPropertyMutation.cfg",
                temporaryDirectory.resolve("full"),
                new ExperimentInputIdentity(
                        artifact(module),
                        artifact(config),
                        artifact(manifest),
                        artifact(tools),
                        artifact(application)));

        Set<String> legacyGaps = legacy.results().stream()
                .filter(result -> result.verdict().equals("state-invariant-gap-candidate"))
                .map(MutationResult::id)
                .collect(Collectors.toSet());
        Set<String> temporalKills = full.results().stream()
                .filter(result -> result.verdict().equals("full-property-temporal-kill"))
                .map(MutationResult::id)
                .collect(Collectors.toSet());
        Set<String> upgraded =
                legacyGaps.stream().filter(temporalKills::contains).collect(Collectors.toSet());

        assertFalse(legacyGaps.isEmpty());
        assertFalse(temporalKills.isEmpty());
        assertFalse(upgraded.isEmpty());
        for (String candidateId : upgraded) {
            MutationResult result = full.results().stream()
                    .filter(candidate -> candidate.id().equals(candidateId))
                    .findFirst()
                    .orElseThrow();
            assertTrue(result.oracleOutcome().startsWith("KILLED_TEMPORAL_"));
            assertEquals(
                    Files.readString(module, UTF_8),
                    result.evidence()
                            .patch()
                            .reverse(result.evidence().patch().apply(Files.readString(module, UTF_8))));
        }
    }

    private static ExperimentInputIdentity.Artifact artifact(Path path) throws Exception {
        return ExperimentInputIdentity.Artifact.from(BoundedFileSnapshot.read(path, 128L * 1024 * 1024));
    }
}
