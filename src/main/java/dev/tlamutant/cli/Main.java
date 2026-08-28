package dev.tlamutant.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.ManifestValidator;
import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.ExperimentRunner;
import dev.tlamutant.report.JsonReportWriter;
import dev.tlamutant.report.MarkdownReportWriter;
import dev.tlamutant.tlc.TlcRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "tla-mutant",
        mixinStandardHelpOptions = true,
        description = "Experimental TLA+ mutation vertical slice.",
        subcommands = {Main.ExperimentCommand.class, Main.ValidateManifestCommand.class})
public final class Main implements Runnable {
    public static void main(String[] arguments) {
        System.exit(new CommandLine(new Main()).execute(arguments));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @CommandLine.Command(
            name = "experiment",
            mixinStandardHelpOptions = true,
            description = "Run the three-family state-invariant experiment.")
    static final class ExperimentCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--model-dir", required = true)
        Path modelDirectory;

        @CommandLine.Option(names = "--module", required = true)
        String module;

        @CommandLine.Option(names = "--config", required = true)
        String config;

        @CommandLine.Option(names = "--tools-jar", required = true)
        Path toolsJar;

        @CommandLine.Option(names = "--work-dir", required = true)
        Path workDirectory;

        @CommandLine.Option(names = "--json-report")
        Path jsonReport;

        @CommandLine.Option(names = "--markdown-report")
        Path markdownReport;

        @CommandLine.Option(names = "--timeout-seconds", defaultValue = "180")
        long timeoutSeconds;

        @Override
        public Integer call() throws Exception {
            Path applicationJar = Path.of(Main.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            ExperimentRunner experiment = new ExperimentRunner(
                    TlcRunner.forCurrentJava(applicationJar, toolsJar), Duration.ofSeconds(timeoutSeconds));
            ExperimentReport report = experiment.run(modelDirectory, module, config, workDirectory);
            if (jsonReport != null) {
                new JsonReportWriter().write(jsonReport, report);
            }
            if (markdownReport != null) {
                Files.createDirectories(markdownReport.toAbsolutePath().getParent());
                Files.writeString(markdownReport, new MarkdownReportWriter().render(report));
            }
            System.out.println(new ObjectMapper().writeValueAsString(report));
            return 0;
        }
    }

    @CommandLine.Command(
            name = "validate-manifest",
            mixinStandardHelpOptions = true,
            description = "Validate corpus entries against the global execution policy.")
    static final class ValidateManifestCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--manifest", required = true)
        Path manifestPath;

        @CommandLine.Option(names = "--policy", required = true)
        Path policyPath;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            CorpusManifest manifest = mapper.readValue(manifestPath.toFile(), CorpusManifest.class);
            ExecutionPolicy policy = mapper.readValue(policyPath.toFile(), ExecutionPolicy.class);
            manifest.entries().forEach(entry -> ManifestValidator.validateEntry(entry, policy.limits()));
            System.out.println("valid");
            return 0;
        }
    }
}
