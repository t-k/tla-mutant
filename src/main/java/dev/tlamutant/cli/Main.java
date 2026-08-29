package dev.tlamutant.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.census.CensusConformanceValidator;
import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.census.CorpusCensusReport;
import dev.tlamutant.census.CorpusCensusRunner;
import dev.tlamutant.census.GcdCensusAnalyzer;
import dev.tlamutant.census.GcdCensusReport;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.DerivedModelWorkspace;
import dev.tlamutant.corpus.ManifestValidator;
import dev.tlamutant.corpus.StratifiedManifestValidator;
import dev.tlamutant.experiment.CorpusExperimentReport;
import dev.tlamutant.experiment.CorpusExperimentRunner;
import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.ExperimentRunner;
import dev.tlamutant.experiment.GcdStratifiedReport;
import dev.tlamutant.experiment.InvariantOracleSets;
import dev.tlamutant.prospective.ProspectiveCensusReport;
import dev.tlamutant.prospective.ProspectiveCensusValidator;
import dev.tlamutant.prospective.ProspectivePreflight;
import dev.tlamutant.report.AtomicJsonWriter;
import dev.tlamutant.report.AtomicTextWriter;
import dev.tlamutant.report.CorpusMarkdownReportWriter;
import dev.tlamutant.report.FrozenJsonWriter;
import dev.tlamutant.report.GcdStratifiedMarkdownWriter;
import dev.tlamutant.report.JsonReportWriter;
import dev.tlamutant.report.MarkdownReportWriter;
import dev.tlamutant.sandbox.SandboxInput;
import dev.tlamutant.security.BoundedFileSnapshot;
import dev.tlamutant.security.ExperimentOutputValidator;
import dev.tlamutant.security.StrictJson;
import dev.tlamutant.security.TemporaryFileSnapshot;
import dev.tlamutant.tlc.TlcRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "tla-mutant",
        mixinStandardHelpOptions = true,
        description = "Experimental TLA+ mutation vertical slice.",
        subcommands = {
            Main.ExperimentCommand.class,
            Main.CensusCommand.class,
            Main.GcdStratifiedCensusCommand.class,
            Main.GcdStratifiedExperimentCommand.class,
            Main.ProspectiveCensusCommand.class,
            Main.CorpusExperimentCommand.class,
            Main.ValidateManifestCommand.class
        })
public final class Main implements Runnable {
    private static final long MAXIMUM_MANIFEST_BYTES = 8L * 1024 * 1024;
    private static final long MAXIMUM_POLICY_BYTES = 1024L * 1024;
    private static final long MAXIMUM_APPLICATION_BYTES = 128L * 1024 * 1024;
    private static final long MAXIMUM_TOOLS_BYTES = 128L * 1024 * 1024;

    public static void main(String[] arguments) {
        System.exit(new CommandLine(new Main()).execute(arguments));
    }

    @CommandLine.Command(
            name = "corpus-experiment",
            mixinStandardHelpOptions = true,
            description = "Fetch and run the pinned 10-20 specification corpus inside rootless Podman.")
    static final class CorpusExperimentCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--manifest", required = true)
        Path manifestPath;

        @CommandLine.Option(names = "--policy", required = true)
        Path policyPath;

        @CommandLine.Option(names = "--tools-jar", required = true)
        Path toolsJar;

        @CommandLine.Option(names = "--cache-dir", required = true)
        Path cacheDirectory;

        @CommandLine.Option(names = "--json-report", required = true)
        Path jsonReport;

        @CommandLine.Option(names = "--markdown-report", required = true)
        Path markdownReport;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            BoundedFileSnapshot manifestSnapshot = BoundedFileSnapshot.read(manifestPath, MAXIMUM_MANIFEST_BYTES);
            BoundedFileSnapshot policySnapshot = BoundedFileSnapshot.read(policyPath, MAXIMUM_POLICY_BYTES);
            BoundedFileSnapshot toolsSnapshot = BoundedFileSnapshot.read(toolsJar, MAXIMUM_TOOLS_BYTES);
            Path applicationJar = applicationJar();
            BoundedFileSnapshot applicationSnapshot =
                    BoundedFileSnapshot.read(applicationJar, MAXIMUM_APPLICATION_BYTES);
            CorpusManifest manifest = manifestSnapshot.parse(mapper, CorpusManifest.class);
            ExecutionPolicy policy = policySnapshot.parse(mapper, ExecutionPolicy.class);
            Files.createDirectories(cacheDirectory.toAbsolutePath().normalize());
            ExperimentOutputValidator.Outputs outputs = ExperimentOutputValidator.validate(
                    jsonReport,
                    markdownReport,
                    List.of(manifestPath, policyPath, toolsJar, applicationJar),
                    List.of(cacheDirectory));
            CorpusExperimentReport report;
            try (TemporaryFileSnapshot frozenApplication =
                            TemporaryFileSnapshot.create(applicationSnapshot, "application.jar");
                    TemporaryFileSnapshot frozenTools = TemporaryFileSnapshot.create(toolsSnapshot, "tla2tools.jar")) {
                report = new CorpusExperimentRunner(
                                frozenApplication.path(), frozenTools.path(), cacheDirectory, policy)
                        .run(manifest);
            }
            new AtomicJsonWriter().write(outputs.json(), report);
            new AtomicTextWriter().write(outputs.markdown(), new CorpusMarkdownReportWriter().render(report));
            System.out.println(mapper.writeValueAsString(report));
            return report.completedEntries() == report.entryCount() ? 0 : 2;
        }
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @CommandLine.Command(
            name = "census",
            mixinStandardHelpOptions = true,
            description = "Report outcome-free GCD action structure for one trusted sandbox input.")
    static final class CensusCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--model-dir", required = true)
        Path modelDirectory;

        @CommandLine.Option(names = "--module", required = true)
        String module;

        @CommandLine.Option(names = "--config", required = true)
        String config;

        @CommandLine.Option(names = "--work-dir")
        Path workDirectory;

        @CommandLine.Option(names = "--derived-source-edit-json")
        List<String> derivedSourceEditJson = new ArrayList<>();

        @Override
        public Integer call() throws Exception {
            List<CorpusManifest.DerivedSourceEdit> edits = parseDerivedSourceEdits(derivedSourceEditJson);
            GcdCensusReport report;
            if (edits.isEmpty()) {
                report = new GcdCensusAnalyzer().analyze(modelDirectory, module, config);
            } else {
                if (workDirectory == null) {
                    throw new IllegalArgumentException("Derived census input requires --work-dir");
                }
                Files.createDirectories(workDirectory);
                try (DerivedModelWorkspace workspace =
                        DerivedModelWorkspace.create(modelDirectory, workDirectory.resolve("derived-model"), edits)) {
                    report = new GcdCensusAnalyzer().analyze(workspace.root(), module, config);
                }
            }
            System.out.println(new ObjectMapper().writeValueAsString(report));
            return 0;
        }
    }

    @CommandLine.Command(
            name = "gcd-stratified-census",
            mixinStandardHelpOptions = true,
            description = "Freeze the outcome-free GCD census for the preregistered corpus.")
    static final class GcdStratifiedCensusCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--manifest", required = true)
        Path manifestPath;

        @CommandLine.Option(names = "--policy", required = true)
        Path policyPath;

        @CommandLine.Option(names = "--tools-jar", required = true)
        Path toolsJar;

        @CommandLine.Option(names = "--cache-dir", required = true)
        Path cacheDirectory;

        @CommandLine.Option(names = "--census-report", required = true)
        Path censusReportPath;

        @CommandLine.Option(names = "--failed-census-report", required = true)
        Path failedCensusReportPath;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            BoundedFileSnapshot manifestSnapshot = BoundedFileSnapshot.read(manifestPath, MAXIMUM_MANIFEST_BYTES);
            BoundedFileSnapshot policySnapshot = BoundedFileSnapshot.read(policyPath, MAXIMUM_POLICY_BYTES);
            BoundedFileSnapshot toolsSnapshot = BoundedFileSnapshot.read(toolsJar, MAXIMUM_TOOLS_BYTES);
            Path applicationJar = applicationJar();
            BoundedFileSnapshot applicationSnapshot =
                    BoundedFileSnapshot.read(applicationJar, MAXIMUM_APPLICATION_BYTES);
            CorpusManifest manifest = manifestSnapshot.parse(mapper, CorpusManifest.class);
            ExecutionPolicy policy = policySnapshot.parse(mapper, ExecutionPolicy.class);
            StratifiedManifestValidator.validate(manifest, policy.limits());
            CensusInputIdentity inputs = inputs(manifestSnapshot, policySnapshot, toolsSnapshot, applicationSnapshot);
            CorpusCensusReport census;
            try (TemporaryFileSnapshot frozenApplication =
                            TemporaryFileSnapshot.create(applicationSnapshot, "application.jar");
                    TemporaryFileSnapshot frozenTools = TemporaryFileSnapshot.create(toolsSnapshot, "tla2tools.jar")) {
                census = new CorpusCensusRunner(frozenApplication.path(), frozenTools.path(), cacheDirectory, policy)
                        .run(manifest, inputs);
            }
            Path validOutput = censusReportPath.toAbsolutePath().normalize();
            Path failedOutput = failedCensusReportPath.toAbsolutePath().normalize();
            if (validOutput.equals(failedOutput)) {
                throw new IllegalArgumentException("Valid and failed census reports require different paths");
            }
            try {
                CensusConformanceValidator.validateBoundInputs(manifest, census, inputs);
            } catch (RuntimeException failure) {
                new FrozenJsonWriter().writeNew(failedOutput, census);
                throw failure;
            }
            new FrozenJsonWriter().writeNew(validOutput, census);
            System.out.println(mapper.writeValueAsString(census));
            return 0;
        }
    }

    @CommandLine.Command(
            name = "prospective-census",
            mixinStandardHelpOptions = true,
            description = "Freeze the failure-preserving census for the receipt-bound prospective holdout.")
    static final class ProspectiveCensusCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--registry", required = true)
        Path registryPath;

        @CommandLine.Option(names = "--protocol", required = true)
        Path protocolPath;

        @CommandLine.Option(names = "--manifest", required = true)
        Path manifestPath;

        @CommandLine.Option(names = "--receipt", required = true)
        Path receiptPath;

        @CommandLine.Option(names = "--policy", required = true)
        Path policyPath;

        @CommandLine.Option(names = "--tools-jar", required = true)
        Path toolsJar;

        @CommandLine.Option(names = "--cache-dir", required = true)
        Path cacheDirectory;

        @CommandLine.Option(names = "--census-report", required = true)
        Path censusReportPath;

        @CommandLine.Option(names = "--failed-census-report", required = true)
        Path failedCensusReportPath;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            Path application = applicationJar();
            ProspectivePreflight.ValidatedDiscoveryInputs validated = new ProspectivePreflight()
                    .validateDiscovery(new ProspectivePreflight.DiscoveryPaths(
                            receiptPath,
                            registryPath,
                            protocolPath,
                            manifestPath,
                            policyPath,
                            toolsJar,
                            application,
                            cacheDirectory,
                            List.of(censusReportPath, failedCensusReportPath)));
            Files.createDirectories(cacheDirectory.toAbsolutePath().normalize());
            CensusInputIdentity inputs = prospectiveInputs(validated);
            CorpusCensusReport census;
            try (TemporaryFileSnapshot frozenApplication =
                            TemporaryFileSnapshot.create(validated.snapshots().get("application"), "application.jar");
                    TemporaryFileSnapshot frozenTools =
                            TemporaryFileSnapshot.create(validated.snapshots().get("tools"), "tla2tools.jar")) {
                census = new CorpusCensusRunner(
                                frozenApplication.path(), frozenTools.path(), cacheDirectory, validated.policy())
                        .run(validated.manifest(), inputs);
            }
            ProspectiveCensusReport report =
                    new ProspectiveCensusReport(1, artifact(validated.receiptSnapshot()), census);
            try {
                ProspectiveCensusValidator.validate(
                        validated.manifest().manifest(), report, artifact(validated.receiptSnapshot()), inputs);
            } catch (RuntimeException failure) {
                new FrozenJsonWriter().writeNew(failedCensusReportPath, report);
                throw failure;
            }
            new FrozenJsonWriter().writeNew(censusReportPath, report);
            System.out.println(mapper.writeValueAsString(report));
            return 0;
        }
    }

    @CommandLine.Command(
            name = "gcd-stratified-experiment",
            mixinStandardHelpOptions = true,
            description = "Run the preregistered corpus only after validating its frozen GCD census.")
    static final class GcdStratifiedExperimentCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--manifest", required = true)
        Path manifestPath;

        @CommandLine.Option(names = "--census-report", required = true)
        Path censusReportPath;

        @CommandLine.Option(names = "--expected-census-sha256", required = true)
        String expectedCensusSha256;

        @CommandLine.Option(names = "--policy", required = true)
        Path policyPath;

        @CommandLine.Option(names = "--tools-jar", required = true)
        Path toolsJar;

        @CommandLine.Option(names = "--cache-dir", required = true)
        Path cacheDirectory;

        @CommandLine.Option(names = "--json-report", required = true)
        Path jsonReportPath;

        @CommandLine.Option(names = "--markdown-report", required = true)
        Path markdownReportPath;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            BoundedFileSnapshot manifestSnapshot = BoundedFileSnapshot.read(manifestPath, MAXIMUM_MANIFEST_BYTES);
            BoundedFileSnapshot policySnapshot = BoundedFileSnapshot.read(policyPath, MAXIMUM_POLICY_BYTES);
            BoundedFileSnapshot toolsSnapshot = BoundedFileSnapshot.read(toolsJar, MAXIMUM_TOOLS_BYTES);
            Path applicationJar = applicationJar();
            BoundedFileSnapshot applicationSnapshot =
                    BoundedFileSnapshot.read(applicationJar, MAXIMUM_APPLICATION_BYTES);
            CorpusManifest manifest = manifestSnapshot.parse(mapper, CorpusManifest.class);
            ExecutionPolicy policy = policySnapshot.parse(mapper, ExecutionPolicy.class);
            StratifiedManifestValidator.validate(manifest, policy.limits());
            long maximumCensusBytes = Math.addExact(
                    Math.multiplyExact(
                            policy.limits().stdoutBytes(), manifest.entries().size()),
                    1_048_576L);
            BoundedFileSnapshot censusSnapshot = BoundedFileSnapshot.read(censusReportPath, maximumCensusBytes);
            if (expectedCensusSha256 == null
                    || !expectedCensusSha256.matches("[0-9a-f]{64}")
                    || !expectedCensusSha256.equals(censusSnapshot.sha256())) {
                throw new IllegalArgumentException("Frozen census SHA-256 does not match the trusted expected value");
            }
            CorpusCensusReport census = censusSnapshot.parse(mapper, CorpusCensusReport.class);
            CensusInputIdentity inputs = inputs(manifestSnapshot, policySnapshot, toolsSnapshot, applicationSnapshot);
            CensusConformanceValidator.validateBoundInputs(manifest, census, inputs);

            Files.createDirectories(cacheDirectory.toAbsolutePath().normalize());
            ExperimentOutputValidator.Outputs outputs = ExperimentOutputValidator.validate(
                    jsonReportPath,
                    markdownReportPath,
                    List.of(manifestPath, censusReportPath, policyPath, toolsJar, applicationJar),
                    List.of(cacheDirectory));
            CorpusExperimentReport experiment;
            try (TemporaryFileSnapshot frozenApplication =
                            TemporaryFileSnapshot.create(applicationSnapshot, "application.jar");
                    TemporaryFileSnapshot frozenTools = TemporaryFileSnapshot.create(toolsSnapshot, "tla2tools.jar")) {
                experiment = new CorpusExperimentRunner(
                                frozenApplication.path(), frozenTools.path(), cacheDirectory, policy)
                        .run(manifest);
            }
            GcdStratifiedReport report = GcdStratifiedReport.create(
                    manifest, census, censusSnapshot.sha256(), censusSnapshot.sizeBytes(), experiment);
            new AtomicJsonWriter().write(outputs.json(), report);
            new AtomicTextWriter().write(outputs.markdown(), new GcdStratifiedMarkdownWriter().render(report));
            System.out.println(mapper.writeValueAsString(report));
            return experiment.completedEntries() == experiment.entryCount() ? 0 : 2;
        }
    }

    private static CensusInputIdentity inputs(
            BoundedFileSnapshot manifest,
            BoundedFileSnapshot policy,
            BoundedFileSnapshot tools,
            BoundedFileSnapshot application) {
        return new CensusInputIdentity(artifact(manifest), artifact(policy), artifact(tools), artifact(application));
    }

    private static CensusInputIdentity prospectiveInputs(ProspectivePreflight.ValidatedDiscoveryInputs validated) {
        return new CensusInputIdentity(
                artifact(validated.snapshots().get("manifest")),
                artifact(validated.snapshots().get("policy")),
                artifact(validated.snapshots().get("tools")),
                artifact(validated.snapshots().get("application")));
    }

    private static CensusInputIdentity.Artifact artifact(BoundedFileSnapshot snapshot) {
        return new CensusInputIdentity.Artifact(snapshot.sha256(), snapshot.sizeBytes());
    }

    private static Path applicationJar() throws Exception {
        return Path.of(
                Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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

        @CommandLine.Option(names = "--state-invariant")
        List<String> stateInvariants = new ArrayList<>();

        @CommandLine.Option(names = "--type-invariant")
        List<String> typeInvariants = new ArrayList<>();

        @CommandLine.Option(names = "--legacy-target-invariant")
        List<String> legacyTargetInvariants = new ArrayList<>();

        @CommandLine.Option(names = "--adopted-target-invariant")
        List<String> adoptedTargetInvariants = new ArrayList<>();

        @CommandLine.Option(names = "--derived-source-edit-json")
        List<String> derivedSourceEditJson = new ArrayList<>();

        @CommandLine.Option(names = "--json-report")
        Path jsonReport;

        @CommandLine.Option(names = "--markdown-report")
        Path markdownReport;

        @CommandLine.Option(names = "--timeout-seconds", defaultValue = "180")
        long timeoutSeconds;

        @CommandLine.Option(names = "--max-states", defaultValue = "1000000")
        long maximumStates;

        @CommandLine.Option(names = "--max-state-bytes", defaultValue = "67108864")
        long maximumStateBytes;

        @Override
        public Integer call() throws Exception {
            Path applicationJar = Path.of(Main.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            ExperimentRunner experiment = new ExperimentRunner(
                    TlcRunner.forCurrentJava(applicationJar, toolsJar, maximumStates, maximumStateBytes),
                    Duration.ofSeconds(timeoutSeconds));
            List<CorpusManifest.DerivedSourceEdit> edits = parseDerivedSourceEdits(derivedSourceEditJson);
            SandboxInput input = new SandboxInput(
                    stateInvariants, typeInvariants, legacyTargetInvariants, adoptedTargetInvariants, edits);
            Files.createDirectories(workDirectory);
            ExperimentReport report;
            if (edits.isEmpty()) {
                report = runExperiment(experiment, modelDirectory, module, config, workDirectory, input);
            } else {
                try (DerivedModelWorkspace workspace =
                        DerivedModelWorkspace.create(modelDirectory, workDirectory.resolve("derived-model"), edits)) {
                    report = runExperiment(experiment, workspace.root(), module, config, workDirectory, input);
                }
            }
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

    private static ExperimentReport runExperiment(
            ExperimentRunner experiment,
            Path modelDirectory,
            String module,
            String config,
            Path workDirectory,
            SandboxInput input) {
        if (!input.flatStateInvariants().isEmpty()
                || (input.typeInvariants().isEmpty()
                        && input.legacyTargetInvariants().isEmpty()
                        && input.adoptedTargetInvariants().isEmpty())) {
            return experiment.run(modelDirectory, module, config, workDirectory, input.flatStateInvariants());
        }
        return experiment.run(
                modelDirectory,
                module,
                config,
                workDirectory,
                new InvariantOracleSets(
                        input.typeInvariants(), input.legacyTargetInvariants(), input.adoptedTargetInvariants()));
    }

    private static List<CorpusManifest.DerivedSourceEdit> parseDerivedSourceEdits(List<String> jsonValues)
            throws Exception {
        if (jsonValues == null || jsonValues.size() > 1) {
            throw new IllegalArgumentException("At most one derived source edit argument is allowed");
        }
        ObjectMapper mapper = new ObjectMapper();
        List<CorpusManifest.DerivedSourceEdit> edits = new ArrayList<>();
        for (String json : jsonValues) {
            if (json == null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16 * 1024) {
                throw new IllegalArgumentException("Derived source edit JSON is too large");
            }
            edits.add(StrictJson.parse(mapper, json, CorpusManifest.DerivedSourceEdit.class));
        }
        return List.copyOf(edits);
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
