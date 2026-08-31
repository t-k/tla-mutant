package dev.tlamutant.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.experiment.OracleMode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class ExperimentCommandTest {
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

    private static Main.ExperimentCommand command(OracleMode mode, Path manifest) {
        Main.ExperimentCommand command = new Main.ExperimentCommand();
        command.oracleMode = mode;
        command.manifestPath = manifest;
        command.timeoutSeconds = 180;
        command.maximumStates = 1_000_000;
        command.maximumStateBytes = 67_108_864;
        return command;
    }
}
