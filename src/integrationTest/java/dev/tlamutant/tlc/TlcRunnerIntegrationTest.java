package dev.tlamutant.tlc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TlcRunnerIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void distinguishesACompletedPassFromAStateInvariantKill() {
        TlcRunner runner = TlcRunner.forCurrentJava(
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")));

        TlcExecution passing = runner.run(
                Path.of("src/testFixtures/resources/tla/mutations"),
                "MutationFixture.tla",
                "MutationFixture.cfg",
                temporaryDirectory.resolve("passing.jsonl"),
                Duration.ofSeconds(30));
        TlcExecution killed = runner.run(
                Path.of("src/testFixtures/resources/tla/tlc"),
                "Violation.tla",
                "Violation.cfg",
                temporaryDirectory.resolve("killed.jsonl"),
                Duration.ofSeconds(30));

        assertEquals(ExecutionStatus.COMPLETED, passing.status());
        assertEquals(OracleOutcome.PASSED, passing.oracle());
        assertEquals(2, passing.states().size());
        assertEquals(ExecutionStatus.COMPLETED, killed.status());
        assertEquals(OracleOutcome.KILLED_STATE_SAFETY, killed.oracle());
        assertFalse(killed.states().isEmpty());
    }

    @Test
    void aBoundedTimeoutIsNeverReportedAsSurvived() {
        TlcRunner runner = TlcRunner.forCurrentJava(
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")));

        TlcExecution timedOut = runner.run(
                Path.of("src/testFixtures/resources/tla/mutations"),
                "MutationFixture.tla",
                "MutationFixture.cfg",
                temporaryDirectory.resolve("timeout.jsonl"),
                Duration.ofMillis(1));

        assertEquals(ExecutionStatus.TIMEOUT, timedOut.status());
        assertEquals(OracleOutcome.NOT_EVALUATED, timedOut.oracle());
    }

    @Test
    void classifiesRealPropertySafetyAndLivenessCounterexamplesWithoutPollutingTheModelDirectory() throws Exception {
        TlcRunner runner = TlcRunner.forCurrentJava(
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")));
        Path sourceDirectory = Path.of("src/testFixtures/resources/tla/full-property");
        Path modelDirectory = temporaryDirectory.resolve("full-property-model");
        Files.createDirectory(modelDirectory);
        for (String fixture :
                new String[] {"NamedTemporal.tla", "NamedTemporal.cfg", "GenericLiveness.tla", "GenericLiveness.cfg"}) {
            Files.copy(sourceDirectory.resolve(fixture), modelDirectory.resolve(fixture));
        }

        TlcExecution temporalSafety = runner.runFullProperty(
                modelDirectory,
                "NamedTemporal.tla",
                "NamedTemporal.cfg",
                temporaryDirectory.resolve("named-temporal.jsonl"),
                Duration.ofSeconds(30));
        TlcExecution liveness = runner.runFullProperty(
                modelDirectory,
                "GenericLiveness.tla",
                "GenericLiveness.cfg",
                temporaryDirectory.resolve("generic-liveness.jsonl"),
                Duration.ofSeconds(30));

        assertEquals(
                ExecutionStatus.COMPLETED,
                temporalSafety.status(),
                temporalSafety.events().toString());
        assertEquals(OracleOutcome.KILLED_STATE_SAFETY, temporalSafety.oracle());
        assertEquals("AlwaysZero", temporalSafety.violatedProperty());
        assertTrue(temporalSafety.events().stream().anyMatch(event -> event.code() == 2110));
        assertEquals(
                ExecutionStatus.COMPLETED, liveness.status(), liveness.events().toString());
        assertEquals(OracleOutcome.KILLED_TEMPORAL_GENERIC, liveness.oracle());
        assertNull(liveness.violatedProperty());
        assertTrue(liveness.events().stream().anyMatch(event -> event.code() == 2116));
        assertFalse(Files.exists(modelDirectory.resolve("states")));
    }

    @Test
    void classifiesRealNamedTemporalAndDeadlockCounterexamples() throws Exception {
        TlcRunner runner = TlcRunner.forCurrentJava(
                Path.of(System.getProperty("tla.mutant.test.appJar")),
                Path.of(System.getProperty("tla.mutant.test.toolsJar")));
        Path modelDirectory = Files.createDirectory(temporaryDirectory.resolve("named-and-deadlock"));
        Files.writeString(
                modelDirectory.resolve("NamedLiveness.tla"),
                "---- MODULE NamedLiveness ----\n"
                        + "VARIABLE x\n"
                        + "Init == x = 0\n"
                        + "Next == x' = x\n"
                        + "Spec == Init /\\ [][Next]_x\n"
                        + "EventuallyOne == <> (x = 1)\n"
                        + "====\n",
                UTF_8);
        Files.writeString(
                modelDirectory.resolve("NamedLiveness.cfg"),
                "SPECIFICATION Spec\nPROPERTY EventuallyOne\nCHECK_DEADLOCK FALSE\n",
                UTF_8);
        Files.writeString(
                modelDirectory.resolve("Deadlock.tla"),
                "---- MODULE Deadlock ----\n"
                        + "VARIABLE x\n"
                        + "Init == x = 0\n"
                        + "Next == /\\ x = 0 /\\ x' = 1\n"
                        + "Spec == Init /\\ [][Next]_x\n"
                        + "====\n",
                UTF_8);
        Files.writeString(modelDirectory.resolve("Deadlock.cfg"), "SPECIFICATION Spec\n", UTF_8);

        TlcExecution namedTemporal = runner.runFullProperty(
                modelDirectory,
                "NamedLiveness.tla",
                "NamedLiveness.cfg",
                temporaryDirectory.resolve("named-liveness.jsonl"),
                Duration.ofSeconds(30));
        TlcExecution deadlock = runner.runFullProperty(
                modelDirectory,
                "Deadlock.tla",
                "Deadlock.cfg",
                temporaryDirectory.resolve("deadlock.jsonl"),
                Duration.ofSeconds(30));

        assertEquals(ExecutionStatus.COMPLETED, namedTemporal.status(), namedTemporal.stderr());
        assertEquals(
                OracleOutcome.KILLED_TEMPORAL_NAMED,
                namedTemporal.oracle(),
                namedTemporal.events().toString());
        assertEquals("EventuallyOne", namedTemporal.violatedProperty());
        assertEquals(ExecutionStatus.COMPLETED, deadlock.status(), deadlock.stderr());
        assertEquals(
                OracleOutcome.KILLED_DEADLOCK,
                deadlock.oracle(),
                deadlock.events().toString());
        assertNull(deadlock.violatedProperty());
    }
}
