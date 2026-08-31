package dev.tlamutant.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.experiment.OracleMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class ExperimentCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void stateInvariantModeRemainsTheCliDefault() {
        CommandLine commandLine = new CommandLine(new Main());
        CommandLine.ParseResult parsed = commandLine.parseArgs(
                "experiment",
                "--model-dir",
                "/model",
                "--module",
                "Spec.tla",
                "--config",
                "Spec.cfg",
                "--tools-jar",
                "/tools.jar",
                "--work-dir",
                "/work");
        Main.ExperimentCommand command =
                (Main.ExperimentCommand) parsed.subcommand().commandSpec().userObject();

        assertEquals(OracleMode.STATE_INVARIANTS, command.oracleMode);
        assertDoesNotThrow(command::validateOptions);
    }

    @Test
    void parsesTheStableFullPropertyModeNameAndRejectsUnknownNames() {
        CommandLine commandLine = new CommandLine(new Main());
        CommandLine.ParseResult parsed = commandLine.parseArgs(
                "experiment",
                "--model-dir",
                "/model",
                "--module",
                "Spec.tla",
                "--config",
                "Spec.cfg",
                "--tools-jar",
                "/tools.jar",
                "--work-dir",
                "/work",
                "--oracle-mode",
                "full-property",
                "--manifest",
                "/manifest.json");
        Main.ExperimentCommand command =
                (Main.ExperimentCommand) parsed.subcommand().commandSpec().userObject();

        assertEquals(OracleMode.FULL_PROPERTY, command.oracleMode);
        assertEquals(Path.of("/manifest.json"), command.manifestPath);
        assertEquals(180, command.timeoutSeconds);
        assertEquals(3_600, command.experimentTimeoutSeconds);
        assertThrows(CommandLine.ParameterException.class, () -> new CommandLine(new Main())
                .parseArgs(
                        "experiment",
                        "--model-dir",
                        "/model",
                        "--module",
                        "Spec.tla",
                        "--config",
                        "Spec.cfg",
                        "--tools-jar",
                        "/tools.jar",
                        "--work-dir",
                        "/work",
                        "--oracle-mode",
                        "full-properties"));
    }

    @Test
    void fullPropertyModeRequiresManifestAndRejectsInvariantReplacement() {
        Main.ExperimentCommand missingManifest = command(OracleMode.FULL_PROPERTY, null);
        Main.ExperimentCommand withInvariant = command(OracleMode.FULL_PROPERTY, Path.of("manifest.json"));
        withInvariant.stateInvariants = List.of("TypeOK");

        assertThrows(IllegalArgumentException.class, missingManifest::validateOptions);
        assertThrows(IllegalArgumentException.class, withInvariant::validateOptions);
        assertDoesNotThrow(command(OracleMode.FULL_PROPERTY, Path.of("manifest.json"))::validateOptions);
        assertDoesNotThrow(command(OracleMode.STATE_INVARIANTS, null)::validateOptions);
    }

    @Test
    void syntheticManifestIdentityIsNotARecognizedCommandOption() {
        assertThrows(CommandLine.ParameterException.class, () -> new CommandLine(new Main())
                .parseArgs("experiment", "--manifest-artifact-json", "{}"));
    }

    @Test
    void fullPropertyModeRejectsUnboundedDirectExecutionLimits() {
        Main.ExperimentCommand command = command(OracleMode.FULL_PROPERTY, Path.of("manifest.json"));
        command.timeoutSeconds = 601;
        command.maximumStates = 1_000_000;
        command.maximumStateBytes = 67_108_864;

        assertThrows(IllegalArgumentException.class, command::validateOptions);
    }

    @Test
    void parsesAnIndependentAggregateExperimentTimeout() {
        CommandLine.ParseResult parsed = new CommandLine(new Main())
                .parseArgs(
                        "experiment",
                        "--model-dir",
                        "/model",
                        "--module",
                        "Spec.tla",
                        "--config",
                        "Spec.cfg",
                        "--tools-jar",
                        "/tools.jar",
                        "--work-dir",
                        "/work",
                        "--oracle-mode",
                        "full-property",
                        "--manifest",
                        "/manifest.json",
                        "--timeout-seconds",
                        "600",
                        "--experiment-timeout-seconds",
                        "3600");
        Main.ExperimentCommand command =
                (Main.ExperimentCommand) parsed.subcommand().commandSpec().userObject();

        assertEquals(600, command.timeoutSeconds);
        assertEquals(3_600, command.experimentTimeoutSeconds);
        assertDoesNotThrow(command::validateOptions);
    }

    @Test
    void rejectsUnsafeAggregateTimeoutBeforeCreatingTheWorkDirectory() {
        for (long invalid : List.of(0L, -1L, 3_601L)) {
            Main.ExperimentCommand command = command(OracleMode.FULL_PROPERTY, Path.of("manifest.json"));
            command.experimentTimeoutSeconds = invalid;
            command.workDirectory = temporaryDirectory.resolve("work-" + invalid);

            assertThrows(IllegalArgumentException.class, command::call);
            assertFalse(Files.exists(command.workDirectory));
        }

        for (long valid : List.of(1L, 3_600L)) {
            Main.ExperimentCommand command = command(OracleMode.FULL_PROPERTY, Path.of("manifest.json"));
            command.experimentTimeoutSeconds = valid;
            assertDoesNotThrow(command::validateOptions);
        }

        Main.ExperimentCommand stateInvariant = command(OracleMode.STATE_INVARIANTS, null);
        stateInvariant.experimentTimeoutSeconds = 0;
        assertDoesNotThrow(stateInvariant::validateOptions);
    }

    private static Main.ExperimentCommand command(OracleMode mode, Path manifest) {
        Main.ExperimentCommand command = new Main.ExperimentCommand();
        command.oracleMode = mode;
        command.manifestPath = manifest;
        command.timeoutSeconds = 180;
        command.experimentTimeoutSeconds = 3_600;
        command.maximumStates = 1_000_000;
        command.maximumStateBytes = 67_108_864;
        return command;
    }
}
