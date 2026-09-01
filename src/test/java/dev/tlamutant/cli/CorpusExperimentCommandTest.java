package dev.tlamutant.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tlamutant.experiment.OracleMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class CorpusExperimentCommandTest {
    @Test
    void defaultsToStateInvariantsAndAcceptsExplicitFullProperty() {
        assertEquals(OracleMode.STATE_INVARIANTS, parse().oracleMode);
        assertEquals(OracleMode.FULL_PROPERTY, parse("--oracle-mode", "full-property").oracleMode);
    }

    private static Main.CorpusExperimentCommand parse(String... additional) {
        List<String> arguments = new ArrayList<>(List.of(
                "corpus-experiment",
                "--manifest",
                "/manifest.json",
                "--policy",
                "/policy.json",
                "--tools-jar",
                "/tools.jar",
                "--cache-dir",
                "/cache",
                "--json-report",
                "/report.json",
                "--markdown-report",
                "/report.md"));
        arguments.addAll(List.of(additional));
        CommandLine.ParseResult parsed = new CommandLine(new Main()).parseArgs(arguments.toArray(String[]::new));
        return (Main.CorpusExperimentCommand) parsed.subcommand().commandSpec().userObject();
    }
}
