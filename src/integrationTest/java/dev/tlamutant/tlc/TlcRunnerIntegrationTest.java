package dev.tlamutant.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
