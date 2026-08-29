package dev.tlamutant.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class ProspectiveInterventionCommandTest {
    @Test
    void exposesReceiptBoundCensusAndExperimentCommands() {
        CommandLine command = new CommandLine(new Main());

        assertTrue(command.getSubcommands().containsKey("prospective-intervention-census"));
        assertTrue(command.getSubcommands().containsKey("prospective-intervention-experiment"));
        assertTrue(command.getSubcommands().containsKey("validate-prospective-closure"));
        assertTrue(command.getSubcommands()
                .get("validate-prospective-closure")
                .getCommandSpec()
                .optionsMap()
                .containsKey("--execution-application"));
    }
}
