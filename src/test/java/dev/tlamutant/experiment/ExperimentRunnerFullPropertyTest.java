package dev.tlamutant.experiment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tlamutant.security.BoundedFileSnapshot;
import dev.tlamutant.tlc.TlcRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExperimentRunnerFullPropertyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesTheCfgAndRecordsTemporalKillsWithReproducibleEvidence() throws Exception {
        Fixture fixture = fixture(false);
        ExperimentRunner runner = new ExperimentRunner(fixture.tlcRunner(), Duration.ofSeconds(30));

        ExperimentReport report = runner.runFullProperty(
                fixture.modelDirectory(),
                "MutationFixture.tla",
                "MutationFixture.cfg",
                fixture.results(),
                fixture.inputs());

        assertEquals(3, report.schemaVersion());
        assertEquals("full-property", report.oracleMode());
        assertEquals(fixture.inputs().module(), report.inputs().module());
        assertEquals(fixture.inputs().config(), report.inputs().config());
        assertFalse(report.inputs().modelFiles().isEmpty());
        assertTrue(report.generatedMutants() > 0);
        assertEquals(report.generatedMutants(), report.evaluatedMutants());
        assertEquals(0, report.gapCandidates());
        assertTrue(
                report.results().stream().allMatch(result -> result.verdict().equals("full-property-temporal-kill")));
        assertTrue(report.results().stream()
                .allMatch(result -> result.oracleOutcome().equals("KILLED_TEMPORAL_NAMED")));
        assertTrue(report.results().stream().allMatch(result -> result.evidence() != null));
        assertTrue(report.results().stream()
                .allMatch(result -> result.evidence().violatedProperty().equals("EventuallyDone")));
        String originalSource = Files.readString(fixture.module(), UTF_8);
        assertTrue(report.results().stream()
                .allMatch(result ->
                        result.evidence().patch().apply(originalSource).contains(result.replacement())));
        assertEquals(
                Files.readAllBytes(fixture.config()).length,
                report.inputs().config().sizeBytes());
        assertFalse(Files.exists(fixture.results().resolve("full-property-model")));
    }

    @Test
    void abortsWhenTheOriginalFullConfigurationDoesNotPass() throws Exception {
        Fixture fixture = fixture(true);
        ExperimentRunner runner = new ExperimentRunner(fixture.tlcRunner(), Duration.ofSeconds(30));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.runFullProperty(
                        fixture.modelDirectory(),
                        "MutationFixture.tla",
                        "MutationFixture.cfg",
                        fixture.results(),
                        fixture.inputs()));

        assertTrue(error.getMessage().contains("KILLED_TEMPORAL_NAMED"));
    }

    @Test
    void stopsLaunchingMutantsAfterTheAggregateDeadline() throws Exception {
        Fixture fixture = fixture(false);
        long expired = Duration.ofSeconds(2).toNanos();
        ExperimentRunner runner = new ExperimentRunner(
                fixture.tlcRunner(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                () -> Files.exists(fixture.launches()) ? expired : 0);

        ExperimentReport report = runner.runFullProperty(
                fixture.modelDirectory(),
                "MutationFixture.tla",
                "MutationFixture.cfg",
                fixture.results(),
                fixture.inputs());

        assertEquals(1, Files.readAllLines(fixture.launches(), UTF_8).size());
        assertTrue(report.generatedMutants() > 0);
        assertEquals(report.generatedMutants(), report.results().size());
        assertEquals(0, report.evaluatedMutants());
        assertEquals(0, report.gapCandidates());
        assertTrue(report.results().stream()
                .allMatch(result -> result.executionStatus().equals("TIMEOUT")));
        assertTrue(report.results().stream()
                .allMatch(result -> result.oracleOutcome().equals("NOT_EVALUATED")));
        assertTrue(report.results().stream()
                .allMatch(result -> result.stateRelation().equals("STATE_UNRESOLVED")));
        assertTrue(report.results().stream().allMatch(result -> result.verdict().equals("unresolved")));
        assertTrue(report.results().stream()
                .allMatch(result -> result.diagnosticReason().equals("experiment-deadline-exhausted")));
        String originalSource = Files.readString(fixture.module(), UTF_8);
        assertTrue(report.results().stream()
                .allMatch(result -> result.evidence() != null
                        && result.evidence().patch().apply(originalSource).contains(result.replacement())));
        assertFalse(Files.exists(fixture.results().resolve("full-property-model")));
    }

    @Test
    void rejectsAnExpiredExperimentBeforeTheBaselineLaunch() throws Exception {
        Fixture fixture = fixture(false);
        AtomicInteger clockReads = new AtomicInteger();
        ExperimentRunner runner = new ExperimentRunner(
                fixture.tlcRunner(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                () -> clockReads.getAndIncrement() == 0
                        ? 0
                        : Duration.ofSeconds(2).toNanos());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.runFullProperty(
                        fixture.modelDirectory(),
                        "MutationFixture.tla",
                        "MutationFixture.cfg",
                        fixture.results(),
                        fixture.inputs()));

        assertTrue(error.getMessage().contains("deadline"));
        assertFalse(Files.exists(fixture.launches()));
    }

    @Test
    void rejectsAnAggregateTimeoutAboveOneHourAtConstruction() throws Exception {
        Fixture fixture = fixture(false);

        assertDoesNotThrow(
                () -> new ExperimentRunner(fixture.tlcRunner(), Duration.ofSeconds(30), Duration.ofHours(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExperimentRunner(
                        fixture.tlcRunner(),
                        Duration.ofSeconds(30),
                        Duration.ofHours(1).plusNanos(1)));
    }

    @Test
    void doesNotAcceptAKillWhenTheAggregateDeadlineExpiresDuringMutantTlc() throws Exception {
        Fixture fixture = fixture(false);
        long expired = Duration.ofSeconds(2).toNanos();
        ExperimentRunner runner = new ExperimentRunner(
                fixture.tlcRunner(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                () -> launchCount(fixture.launches()) >= 2 ? expired : 0);

        ExperimentReport report = runner.runFullProperty(
                fixture.modelDirectory(),
                "MutationFixture.tla",
                "MutationFixture.cfg",
                fixture.results(),
                fixture.inputs());

        assertEquals(2, launchCount(fixture.launches()));
        assertEquals(report.generatedMutants(), report.results().size());
        assertEquals(0, report.evaluatedMutants());
        assertEquals(0, report.gapCandidates());
        assertTrue(report.results().stream()
                .allMatch(result -> result.executionStatus().equals("TIMEOUT")));
        assertTrue(report.results().stream()
                .allMatch(result -> result.oracleOutcome().equals("NOT_EVALUATED")));
        assertTrue(report.results().stream()
                .allMatch(result -> result.diagnosticReason().equals("experiment-deadline-exhausted")));
    }

    private Fixture fixture(boolean killBaseline) throws Exception {
        Path model = temporaryDirectory.resolve(killBaseline ? "baseline-kill-model" : "model");
        Path results = temporaryDirectory.resolve(killBaseline ? "baseline-kill-results" : "results");
        Files.createDirectories(model);
        String moduleText =
                Files.readString(Path.of("src/testFixtures/resources/tla/mutations/MutationFixture.tla"), UTF_8);
        moduleText =
                moduleText.replace("ScopeOnly == x <= 20\n", "ScopeOnly == x <= 20\nEventuallyDone == <> (x = 3)\n");
        Path module = model.resolve("MutationFixture.tla");
        Path config = model.resolve("MutationFixture.cfg");
        Files.writeString(module, moduleText, UTF_8);
        Files.writeString(
                config,
                Files.readString(Path.of("src/testFixtures/resources/tla/mutations/MutationFixture.cfg"), UTF_8)
                        + "PROPERTY EventuallyDone\n",
                UTF_8);
        Path manifest = temporaryDirectory.resolve(killBaseline ? "baseline-kill-manifest.json" : "manifest.json");
        Path tools = temporaryDirectory.resolve(killBaseline ? "baseline-kill-tools.jar" : "tools.jar");
        Path application =
                temporaryDirectory.resolve(killBaseline ? "baseline-kill-application.jar" : "application.jar");
        Files.writeString(manifest, "{\"schemaVersion\":1}\n", UTF_8);
        Files.writeString(tools, "tools\n", UTF_8);
        Files.writeString(application, "application\n", UTF_8);
        Path java = temporaryDirectory.resolve(killBaseline ? "baseline-kill-java" : "fake-java");
        Path launches = temporaryDirectory.resolve(killBaseline ? "baseline-kill-launches" : "launches");
        String originalModule = shellQuote(module.toAbsolutePath().toString());
        String originalConfig = shellQuote(config.toAbsolutePath().toString());
        String baselineCondition = killBaseline ? "false" : "cmp -s \"$module\" " + originalModule;
        Files.writeString(
                java,
                "#!/bin/sh\n"
                        + "printf '%s\\n' launch >> "
                        + shellQuote(launches.toAbsolutePath().toString())
                        + "\n"
                        + "state=''\nconfig=''\nmodule=''\nprevious=''\n"
                        + "for argument in \"$@\"; do\n"
                        + "  case \"$argument\" in -Dtla.mutant.state.path=*) state=${argument#*=};; esac\n"
                        + "  if [ \"$previous\" = '-config' ]; then config=$argument; fi\n"
                        + "  previous=$argument\nmodule=$argument\n"
                        + "done\n"
                        + "cmp -s \"$config\" "
                        + originalConfig
                        + " || exit 90\n"
                        + "if "
                        + baselineCondition
                        + "; then\n"
                        + "  printf '%s\\n' '{\"type\":\"complete\",\"states\":0}' > \"$state\"\n"
                        + "  printf '%s\\n' '@!@!@STARTMSG 2193:0 @!@!@' 'Model checking completed.' '@!@!@ENDMSG 2193 @!@!@'\n"
                        + "  exit 0\n"
                        + "fi\n"
                        + "printf '%s\\n' '{\"type\":\"complete\",\"states\":0}' > \"$state\"\n"
                        + "printf '%s\\n' '@!@!@STARTMSG 2116:1 @!@!@' 'Temporal property EventuallyDone was violated.' '@!@!@ENDMSG 2116 @!@!@'\n"
                        + "exit 13\n",
                UTF_8);
        try {
            Files.setPosixFilePermissions(
                    java,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            assertTrue(java.toFile().setExecutable(true));
        }
        TlcRunner tlcRunner =
                new TlcRunner(java.toAbsolutePath(), application.toAbsolutePath(), tools.toAbsolutePath());
        ExperimentInputIdentity inputs = new ExperimentInputIdentity(
                artifact(module), artifact(config), artifact(manifest), artifact(tools), artifact(application));
        return new Fixture(model, module, config, results, launches, tlcRunner, inputs);
    }

    private static ExperimentInputIdentity.Artifact artifact(Path path) throws Exception {
        return ExperimentInputIdentity.Artifact.from(BoundedFileSnapshot.read(path, 8L * 1024 * 1024));
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static int launchCount(Path launches) {
        if (Files.notExists(launches)) {
            return 0;
        }
        try {
            return Files.readAllLines(launches, UTF_8).size();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot count fake TLC launches", exception);
        }
    }

    private record Fixture(
            Path modelDirectory,
            Path module,
            Path config,
            Path results,
            Path launches,
            TlcRunner tlcRunner,
            ExperimentInputIdentity inputs) {}
}
