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
import dev.tlamutant.corpus.ProspectiveInterventionValidator;
import dev.tlamutant.corpus.StratifiedManifestValidator;
import dev.tlamutant.experiment.CorpusExperimentInputIdentity;
import dev.tlamutant.experiment.CorpusExperimentReport;
import dev.tlamutant.experiment.CorpusExperimentRunner;
import dev.tlamutant.experiment.ExperimentInputIdentity;
import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.ExperimentRunner;
import dev.tlamutant.experiment.GcdStratifiedReport;
import dev.tlamutant.experiment.InvariantOracleSets;
import dev.tlamutant.experiment.OracleMode;
import dev.tlamutant.prospective.ProspectiveAssessment;
import dev.tlamutant.prospective.ProspectiveCensusReport;
import dev.tlamutant.prospective.ProspectiveCensusValidator;
import dev.tlamutant.prospective.ProspectiveClosureAssessment;
import dev.tlamutant.prospective.ProspectiveClosureProvenance;
import dev.tlamutant.prospective.ProspectiveClosureValidator;
import dev.tlamutant.prospective.ProspectiveDiscoveryReport;
import dev.tlamutant.prospective.ProspectiveInterventionPreflight;
import dev.tlamutant.prospective.ProspectivePreflight;
import dev.tlamutant.prospective.ProspectiveProtocol;
import dev.tlamutant.prospective.ProspectiveReceipt;
import dev.tlamutant.prospective.ProspectiveReviewDossier;
import dev.tlamutant.prospective.ProspectiveReviewLedger;
import dev.tlamutant.prospective.ProspectiveReviewValidator;
import dev.tlamutant.prospective.ProspectiveSemanticComparator;
import dev.tlamutant.report.AtomicJsonWriter;
import dev.tlamutant.report.AtomicTextWriter;
import dev.tlamutant.report.CorpusMarkdownReportWriter;
import dev.tlamutant.report.FrozenJsonWriter;
import dev.tlamutant.report.FrozenTextWriter;
import dev.tlamutant.report.GcdStratifiedMarkdownWriter;
import dev.tlamutant.report.JsonReportWriter;
import dev.tlamutant.report.MarkdownReportWriter;
import dev.tlamutant.report.ProspectiveMarkdownWriter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            Main.ProspectiveExperimentCommand.class,
            Main.CompareProspectiveReportsCommand.class,
            Main.ProspectiveReviewDossierCommand.class,
            Main.ValidateProspectiveReviewCommand.class,
            Main.ProspectiveInterventionCensusCommand.class,
            Main.ProspectiveInterventionExperimentCommand.class,
            Main.ValidateProspectiveClosureCommand.class,
            Main.CorpusExperimentCommand.class,
            Main.ValidateManifestCommand.class
        })
public final class Main implements Runnable {
    private static final long MAXIMUM_MANIFEST_BYTES = 8L * 1024 * 1024;
    private static final long MAXIMUM_POLICY_BYTES = 1024L * 1024;
    private static final long MAXIMUM_APPLICATION_BYTES = 128L * 1024 * 1024;
    private static final long MAXIMUM_TOOLS_BYTES = 128L * 1024 * 1024;
    private static final long MAXIMUM_MODULE_BYTES = 64L * 1024 * 1024;
    private static final long MAXIMUM_CONFIG_BYTES = 8L * 1024 * 1024;
    private static final long MAXIMUM_PROSPECTIVE_REPORT_BYTES = 256L * 1024 * 1024;
    private static final long MAXIMUM_PROTOCOL_BYTES = 2L * 1024 * 1024;

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

        @CommandLine.Option(
                names = "--oracle-mode",
                defaultValue = "state-invariants",
                converter = OracleModeConverter.class)
        OracleMode oracleMode = OracleMode.STATE_INVARIANTS;

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
            CorpusExperimentInputIdentity experimentInputs = oracleMode == OracleMode.FULL_PROPERTY
                    ? new CorpusExperimentInputIdentity(
                            ExperimentInputIdentity.Artifact.from(manifestSnapshot),
                            ExperimentInputIdentity.Artifact.from(toolsSnapshot),
                            ExperimentInputIdentity.Artifact.from(applicationSnapshot))
                    : null;
            try (TemporaryFileSnapshot frozenApplication =
                            TemporaryFileSnapshot.create(applicationSnapshot, "application.jar");
                    TemporaryFileSnapshot frozenTools = TemporaryFileSnapshot.create(toolsSnapshot, "tla2tools.jar")) {
                report = new CorpusExperimentRunner(
                                frozenApplication.path(),
                                frozenTools.path(),
                                cacheDirectory,
                                policy,
                                oracleMode,
                                experimentInputs)
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
            name = "prospective-experiment",
            mixinStandardHelpOptions = true,
            description = "Run prospective mutations only after validating the receipt-bound frozen census.")
    static final class ProspectiveExperimentCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--registry", required = true)
        Path registryPath;

        @CommandLine.Option(names = "--protocol", required = true)
        Path protocolPath;

        @CommandLine.Option(names = "--manifest", required = true)
        Path manifestPath;

        @CommandLine.Option(names = "--receipt", required = true)
        Path receiptPath;

        @CommandLine.Option(names = "--discovery-receipt", required = true)
        Path discoveryReceiptPath;

        @CommandLine.Option(names = "--census-report", required = true)
        Path censusReportPath;

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
            ProspectivePreflight.ValidatedExperimentInputs validated = new ProspectivePreflight()
                    .validateExperiment(new ProspectivePreflight.ExperimentPaths(
                            receiptPath,
                            discoveryReceiptPath,
                            registryPath,
                            protocolPath,
                            manifestPath,
                            policyPath,
                            toolsJar,
                            applicationJar(),
                            censusReportPath,
                            cacheDirectory,
                            List.of(jsonReportPath, markdownReportPath)));
            ProspectiveCensusReport census = validated.censusSnapshot().parse(mapper, ProspectiveCensusReport.class);
            CensusInputIdentity inputs = prospectiveInputs(validated.discovery());
            ProspectiveCensusValidator.validate(
                    validated.discovery().manifest().manifest(),
                    census,
                    artifact(validated.discoveryReceiptSnapshot()),
                    inputs);
            Files.createDirectories(cacheDirectory.toAbsolutePath().normalize());
            CorpusExperimentReport experiment;
            try (TemporaryFileSnapshot frozenApplication = TemporaryFileSnapshot.create(
                            validated.discovery().snapshots().get("application"), "application.jar");
                    TemporaryFileSnapshot frozenTools = TemporaryFileSnapshot.create(
                            validated.discovery().snapshots().get("tools"), "tla2tools.jar")) {
                experiment = new CorpusExperimentRunner(
                                frozenApplication.path(),
                                frozenTools.path(),
                                cacheDirectory,
                                validated.discovery().policy())
                        .run(validated.discovery().manifest(), census);
            }
            ProspectiveDiscoveryReport report = ProspectiveDiscoveryReport.create(
                    validated.discovery().manifest().manifest(),
                    census,
                    artifact(validated.censusSnapshot()),
                    artifact(validated.discovery().receiptSnapshot()),
                    experiment);
            new FrozenJsonWriter().writeNew(jsonReportPath, report);
            new FrozenTextWriter()
                    .writeNew(markdownReportPath, new ProspectiveMarkdownWriter().renderDiscovery(report));
            System.out.println(mapper.writeValueAsString(report));
            return 0;
        }
    }

    @CommandLine.Command(
            name = "compare-prospective-reports",
            mixinStandardHelpOptions = true,
            description = "Compare two prospective reports after removing declared diagnostic metadata.")
    static final class CompareProspectiveReportsCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--left", required = true)
        Path leftPath;

        @CommandLine.Option(names = "--right", required = true)
        Path rightPath;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            ProspectiveDiscoveryReport left = BoundedFileSnapshot.read(leftPath, MAXIMUM_PROSPECTIVE_REPORT_BYTES)
                    .parse(mapper, ProspectiveDiscoveryReport.class);
            ProspectiveDiscoveryReport right = BoundedFileSnapshot.read(rightPath, MAXIMUM_PROSPECTIVE_REPORT_BYTES)
                    .parse(mapper, ProspectiveDiscoveryReport.class);
            ProspectiveSemanticComparator.Comparison comparison =
                    new ProspectiveSemanticComparator().compare(left, right);
            System.out.println(mapper.writeValueAsString(comparison));
            return comparison.equal() ? 0 : 2;
        }
    }

    @CommandLine.Command(
            name = "prospective-review-dossier",
            mixinStandardHelpOptions = true,
            description = "Derive the complete receipt-bound set of prospective review candidates.")
    static final class ProspectiveReviewDossierCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--receipt", required = true)
        Path receiptPath;

        @CommandLine.Option(names = "--protocol", required = true)
        Path protocolPath;

        @CommandLine.Option(names = "--manifest", required = true)
        Path manifestPath;

        @CommandLine.Option(names = "--census-report", required = true)
        Path censusPath;

        @CommandLine.Option(names = "--discovery-report", required = true)
        Path discoveryPath;

        @CommandLine.Option(names = "--output", required = true)
        Path outputPath;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            Path output = outputPath.toAbsolutePath().normalize();
            if (Files.exists(output)) {
                throw new IllegalArgumentException("Prospective canonical output already exists");
            }
            List<Path> inputs = List.of(receiptPath, protocolPath, manifestPath, censusPath, discoveryPath).stream()
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
            if (inputs.stream().distinct().count() != inputs.size() || inputs.contains(output)) {
                throw new IllegalArgumentException("Prospective dossier paths must be disjoint");
            }
            BoundedFileSnapshot receiptSnapshot = BoundedFileSnapshot.read(receiptPath, MAXIMUM_POLICY_BYTES);
            BoundedFileSnapshot protocolSnapshot = BoundedFileSnapshot.read(protocolPath, MAXIMUM_POLICY_BYTES * 2);
            BoundedFileSnapshot manifestSnapshot = BoundedFileSnapshot.read(manifestPath, MAXIMUM_MANIFEST_BYTES);
            BoundedFileSnapshot censusSnapshot = BoundedFileSnapshot.read(censusPath, MAXIMUM_PROSPECTIVE_REPORT_BYTES);
            BoundedFileSnapshot discoverySnapshot =
                    BoundedFileSnapshot.read(discoveryPath, MAXIMUM_PROSPECTIVE_REPORT_BYTES);
            ProspectiveReceipt receipt = receiptSnapshot.parse(mapper, ProspectiveReceipt.class);
            ProspectiveDiscoveryReport discovery = discoverySnapshot.parse(mapper, ProspectiveDiscoveryReport.class);
            CorpusManifest manifest = manifestSnapshot.parse(mapper, CorpusManifest.class);
            if (!receipt.phase().equals("experiment")
                    || !artifact(receiptSnapshot).equals(discovery.experimentReceipt())
                    || !receiptMatches(receipt.artifact("protocol"), protocolSnapshot)
                    || !receiptMatches(receipt.artifact("manifest"), manifestSnapshot)
                    || !receiptMatches(receipt.artifact("census"), censusSnapshot)
                    || !artifact(censusSnapshot).equals(discovery.censusArtifact())) {
                throw new IllegalArgumentException("Prospective dossier inputs do not match frozen discovery");
            }
            ProspectiveReviewDossier dossier = ProspectiveReviewDossier.derive(
                    manifest,
                    discovery,
                    artifact(protocolSnapshot),
                    artifact(manifestSnapshot),
                    artifact(discoverySnapshot));
            new FrozenJsonWriter().writeNew(output, dossier);
            System.out.println(mapper.writeValueAsString(dossier));
            return 0;
        }
    }

    @CommandLine.Command(
            name = "validate-prospective-review",
            mixinStandardHelpOptions = true,
            description = "Validate a complete user-delegated review and publish its assessment.")
    static final class ValidateProspectiveReviewCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--dossier", required = true)
        Path dossierPath;

        @CommandLine.Option(names = "--review", required = true)
        Path reviewPath;

        @CommandLine.Option(names = "--protocol", required = true)
        Path protocolPath;

        @CommandLine.Option(names = "--assessment", required = true)
        Path assessmentPath;

        @Override
        public Integer call() throws Exception {
            Path dossier = dossierPath.toAbsolutePath().normalize();
            Path review = reviewPath.toAbsolutePath().normalize();
            Path protocolPathNormalized = protocolPath.toAbsolutePath().normalize();
            Path assessment = assessmentPath.toAbsolutePath().normalize();
            if (dossier.equals(review)
                    || dossier.equals(protocolPathNormalized)
                    || dossier.equals(assessment)
                    || review.equals(protocolPathNormalized)
                    || review.equals(assessment)
                    || protocolPathNormalized.equals(assessment)
                    || Files.exists(assessment)) {
                throw new IllegalArgumentException("Prospective review paths must be disjoint and output must be new");
            }
            ObjectMapper mapper = new ObjectMapper();
            BoundedFileSnapshot dossierSnapshot = BoundedFileSnapshot.read(dossier, MAXIMUM_PROSPECTIVE_REPORT_BYTES);
            BoundedFileSnapshot reviewSnapshot = BoundedFileSnapshot.read(review, MAXIMUM_PROSPECTIVE_REPORT_BYTES);
            BoundedFileSnapshot protocolSnapshot =
                    BoundedFileSnapshot.read(protocolPathNormalized, MAXIMUM_PROTOCOL_BYTES);
            ProspectiveReviewDossier parsedDossier = dossierSnapshot.parse(mapper, ProspectiveReviewDossier.class);
            ProspectiveReviewLedger ledger = reviewSnapshot.parse(mapper, ProspectiveReviewLedger.class);
            ProspectiveProtocol protocol = protocolSnapshot.parse(mapper, ProspectiveProtocol.class);
            ProspectiveReviewValidator.ValidatedReview validated = ProspectiveReviewValidator.validate(
                    parsedDossier, artifact(dossierSnapshot), ledger, protocol, artifact(protocolSnapshot));
            ProspectiveAssessment result = ProspectiveAssessment.review(parsedDossier, validated);
            new FrozenTextWriter().writeNew(assessment, new ProspectiveMarkdownWriter().renderReview(result));
            System.out.println(mapper.writeValueAsString(result));
            return 0;
        }
    }

    static final class ProspectiveInterventionInputs {
        @CommandLine.Option(names = "--v10-discovery-receipt", required = true)
        Path v10DiscoveryReceipt;

        @CommandLine.Option(names = "--v10-experiment-receipt", required = true)
        Path v10ExperimentReceipt;

        @CommandLine.Option(names = "--v10-protocol", required = true)
        Path v10Protocol;

        @CommandLine.Option(names = "--v10-manifest", required = true)
        Path v10Manifest;

        @CommandLine.Option(names = "--v10-census", required = true)
        Path v10Census;

        @CommandLine.Option(names = "--v10-discovery", required = true)
        Path v10Discovery;

        @CommandLine.Option(names = "--dossier", required = true)
        Path dossier;

        @CommandLine.Option(names = "--review", required = true)
        Path review;

        @CommandLine.Option(names = "--v11-protocol", required = true)
        Path v11Protocol;

        @CommandLine.Option(names = "--v11-manifest", required = true)
        Path v11Manifest;

        @CommandLine.Option(names = "--policy", required = true)
        Path policy;

        @CommandLine.Option(names = "--tools-jar", required = true)
        Path tools;

        Map<String, Path> paths(Path application) {
            Map<String, Path> paths = new LinkedHashMap<>();
            paths.put("v10-discovery-receipt", v10DiscoveryReceipt);
            paths.put("v10-experiment-receipt", v10ExperimentReceipt);
            paths.put("v10-protocol", v10Protocol);
            paths.put("v10-manifest", v10Manifest);
            paths.put("v10-census", v10Census);
            paths.put("v10-discovery", v10Discovery);
            paths.put("dossier", dossier);
            paths.put("review", review);
            paths.put("v11-protocol", v11Protocol);
            paths.put("v11-manifest", v11Manifest);
            paths.put("policy", policy);
            paths.put("tools", tools);
            paths.put("application", application);
            return paths;
        }
    }

    @CommandLine.Command(
            name = "prospective-intervention-census",
            mixinStandardHelpOptions = true,
            description = "Freeze the review-bound v11 census before intervention mutation.")
    static final class ProspectiveInterventionCensusCommand implements Callable<Integer> {
        @CommandLine.Mixin
        ProspectiveInterventionInputs inputs;

        @CommandLine.Option(names = "--receipt", required = true)
        Path receipt;

        @CommandLine.Option(names = "--cache-dir", required = true)
        Path cacheDirectory;

        @CommandLine.Option(names = "--census-report", required = true)
        Path censusReport;

        @CommandLine.Option(names = "--failed-census-report", required = true)
        Path failedCensusReport;

        @Override
        public Integer call() throws Exception {
            ProspectiveInterventionPreflight.ValidatedInputs validated = new ProspectiveInterventionPreflight()
                    .validate(new ProspectiveInterventionPreflight.Paths(
                            receipt,
                            inputs.paths(applicationJar()),
                            cacheDirectory,
                            List.of(censusReport, failedCensusReport)));
            Files.createDirectories(cacheDirectory.toAbsolutePath().normalize());
            CensusInputIdentity inputIdentity = ProspectiveInterventionPreflight.inputs(validated.snapshots());
            CorpusCensusReport census;
            try (TemporaryFileSnapshot frozenApplication =
                            TemporaryFileSnapshot.create(validated.snapshots().get("application"), "application.jar");
                    TemporaryFileSnapshot frozenTools =
                            TemporaryFileSnapshot.create(validated.snapshots().get("tools"), "tla2tools.jar")) {
                census = new CorpusCensusRunner(
                                frozenApplication.path(), frozenTools.path(), cacheDirectory, validated.policy())
                        .run(validated.manifest(), inputIdentity);
            }
            ProspectiveCensusReport report = new ProspectiveCensusReport(
                    1, ProspectiveInterventionPreflight.artifact(validated.receiptSnapshot()), census);
            try {
                ProspectiveCensusValidator.validate(
                        validated.manifest().manifest(),
                        report,
                        ProspectiveInterventionPreflight.artifact(validated.receiptSnapshot()),
                        inputIdentity);
                ProspectiveInterventionValidator.validate(
                        validated.v10Manifest(),
                        validated.manifest().manifest(),
                        validated.dossier(),
                        validated.ledger(),
                        validated.review(),
                        report,
                        validated.policy().limits());
            } catch (RuntimeException failure) {
                new FrozenJsonWriter().writeNew(failedCensusReport, report);
                throw failure;
            }
            new FrozenJsonWriter().writeNew(censusReport, report);
            System.out.println(new ObjectMapper().writeValueAsString(report));
            return 0;
        }
    }

    @CommandLine.Command(
            name = "prospective-intervention-experiment",
            mixinStandardHelpOptions = true,
            description = "Run review-bound v11 mutations against the frozen intervention census.")
    static final class ProspectiveInterventionExperimentCommand implements Callable<Integer> {
        @CommandLine.Mixin
        ProspectiveInterventionInputs inputs;

        @CommandLine.Option(names = "--receipt", required = true)
        Path receipt;

        @CommandLine.Option(names = "--census-receipt", required = true)
        Path censusReceipt;

        @CommandLine.Option(names = "--v11-census", required = true)
        Path v11Census;

        @CommandLine.Option(names = "--cache-dir", required = true)
        Path cacheDirectory;

        @CommandLine.Option(names = "--json-report", required = true)
        Path jsonReport;

        @CommandLine.Option(names = "--markdown-report", required = true)
        Path markdownReport;

        @Override
        public Integer call() throws Exception {
            Map<String, Path> paths = inputs.paths(applicationJar());
            paths.put("census-receipt", censusReceipt);
            paths.put("v11-census", v11Census);
            ProspectiveInterventionPreflight.ValidatedInputs validated = new ProspectiveInterventionPreflight()
                    .validate(new ProspectiveInterventionPreflight.Paths(
                            receipt, paths, cacheDirectory, List.of(jsonReport, markdownReport)));
            Files.createDirectories(cacheDirectory.toAbsolutePath().normalize());
            CorpusExperimentReport experiment;
            try (TemporaryFileSnapshot frozenApplication =
                            TemporaryFileSnapshot.create(validated.snapshots().get("application"), "application.jar");
                    TemporaryFileSnapshot frozenTools =
                            TemporaryFileSnapshot.create(validated.snapshots().get("tools"), "tla2tools.jar")) {
                experiment = new CorpusExperimentRunner(
                                frozenApplication.path(), frozenTools.path(), cacheDirectory, validated.policy())
                        .run(validated.manifest(), validated.census());
            }
            ProspectiveDiscoveryReport report = ProspectiveDiscoveryReport.create(
                    validated.manifest().manifest(),
                    validated.census(),
                    ProspectiveInterventionPreflight.artifact(
                            validated.snapshots().get("v11-census")),
                    ProspectiveInterventionPreflight.artifact(validated.receiptSnapshot()),
                    experiment);
            new FrozenJsonWriter().writeNew(jsonReport, report);
            new FrozenTextWriter().writeNew(markdownReport, new ProspectiveMarkdownWriter().renderDiscovery(report));
            System.out.println(new ObjectMapper().writeValueAsString(report));
            return 0;
        }
    }

    @CommandLine.Command(
            name = "validate-prospective-closure",
            mixinStandardHelpOptions = true,
            description = "Derive a fail-closed assessment from the frozen review and corrected v11 result.")
    static final class ValidateProspectiveClosureCommand implements Callable<Integer> {
        @CommandLine.Mixin
        ProspectiveInterventionInputs inputs;

        @CommandLine.Option(names = "--receipt", required = true)
        Path receipt;

        @CommandLine.Option(names = "--census-receipt", required = true)
        Path censusReceipt;

        @CommandLine.Option(names = "--v11-census", required = true)
        Path v11Census;

        @CommandLine.Option(names = "--v11-result", required = true)
        Path v11Result;

        @CommandLine.Option(
                names = "--execution-application",
                required = true,
                description = "Application JAR whose digest is bound by the experiment receipt.")
        Path executionApplication;

        @CommandLine.Option(names = "--cache-dir", required = true)
        Path cacheDirectory;

        @CommandLine.Option(names = "--output", required = true)
        Path output;

        @Override
        public Integer call() throws Exception {
            Path resultPath = v11Result.toAbsolutePath().normalize();
            Path outputPath = output.toAbsolutePath().normalize();
            if (resultPath.equals(outputPath) || Files.exists(outputPath)) {
                throw new IllegalArgumentException("Prospective closure result and output paths must be disjoint");
            }
            Map<String, Path> paths = inputs.paths(executionApplication);
            paths.put("census-receipt", censusReceipt);
            paths.put("v11-census", v11Census);
            ProspectiveInterventionPreflight.ValidatedInputs validated = new ProspectiveInterventionPreflight()
                    .validate(new ProspectiveInterventionPreflight.Paths(
                            receipt, paths, cacheDirectory, List.of(outputPath)));
            BoundedFileSnapshot resultSnapshot = BoundedFileSnapshot.read(resultPath, MAXIMUM_PROSPECTIVE_REPORT_BYTES);
            ProspectiveDiscoveryReport result =
                    resultSnapshot.parse(new ObjectMapper(), ProspectiveDiscoveryReport.class);
            ProspectiveDiscoveryReport reconstructed = ProspectiveDiscoveryReport.create(
                    validated.manifest().manifest(),
                    validated.census(),
                    ProspectiveInterventionPreflight.artifact(
                            validated.snapshots().get("v11-census")),
                    ProspectiveInterventionPreflight.artifact(validated.receiptSnapshot()),
                    result.experiment());
            if (!result.equals(reconstructed)) {
                throw new IllegalArgumentException("Prospective closure result is not derived from its frozen inputs");
            }
            BoundedFileSnapshot validatorSnapshot =
                    BoundedFileSnapshot.read(applicationJar(), MAXIMUM_APPLICATION_BYTES);
            ProspectiveClosureAssessment assessment = ProspectiveClosureValidator.evaluate(
                    validated.dossier(),
                    validated.ledger(),
                    validated.review(),
                    validated.v10Discovery(),
                    result,
                    ProspectiveClosureProvenance.create(
                            validated.snapshots(), validated.receiptSnapshot(), resultSnapshot, validatorSnapshot));
            new FrozenJsonWriter().writeNew(outputPath, assessment);
            System.out.println(new ObjectMapper().writeValueAsString(assessment));
            return assessment.closureStatus().equals("VERIFIED") ? 0 : 2;
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

    private static boolean receiptMatches(ProspectiveReceipt.Artifact expected, BoundedFileSnapshot actual) {
        return expected.sha256().equals(actual.sha256()) && expected.sizeBytes() == actual.sizeBytes();
    }

    private static Path applicationJar() throws Exception {
        return Path.of(
                Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    @CommandLine.Command(
            name = "experiment",
            mixinStandardHelpOptions = true,
            description = "Run the three-family mutation experiment.")
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

        @CommandLine.Option(
                names = "--oracle-mode",
                defaultValue = "state-invariants",
                converter = OracleModeConverter.class)
        OracleMode oracleMode = OracleMode.STATE_INVARIANTS;

        @CommandLine.Option(names = "--manifest")
        Path manifestPath;

        @CommandLine.Option(names = "--manifest-artifact-json", hidden = true)
        String manifestArtifactJson;

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
            validateOptions();
            List<CorpusManifest.DerivedSourceEdit> edits = parseDerivedSourceEdits(derivedSourceEditJson);
            SandboxInput input = new SandboxInput(
                    stateInvariants, typeInvariants, legacyTargetInvariants, adoptedTargetInvariants, edits);
            Files.createDirectories(workDirectory);
            ExperimentReport report;
            if (edits.isEmpty()) {
                report = runSelectedExperiment(modelDirectory, input);
            } else {
                try (DerivedModelWorkspace workspace =
                        DerivedModelWorkspace.create(modelDirectory, workDirectory.resolve("derived-model"), edits)) {
                    report = runSelectedExperiment(workspace.root(), input);
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

        void validateOptions() {
            if (oracleMode == null) {
                throw new IllegalArgumentException("Oracle mode is required");
            }
            boolean hasInvariantReplacement = !stateInvariants.isEmpty()
                    || !typeInvariants.isEmpty()
                    || !legacyTargetInvariants.isEmpty()
                    || !adoptedTargetInvariants.isEmpty();
            boolean hasManifestPath = manifestPath != null;
            boolean hasManifestArtifact = manifestArtifactJson != null;
            if (oracleMode == OracleMode.FULL_PROPERTY && hasManifestPath == hasManifestArtifact) {
                throw new IllegalArgumentException("Full-property mode requires exactly one manifest identity source");
            }
            if (oracleMode == OracleMode.FULL_PROPERTY && hasInvariantReplacement) {
                throw new IllegalArgumentException("Full-property mode does not accept invariant replacement options");
            }
            if (oracleMode == OracleMode.STATE_INVARIANTS && hasManifestArtifact) {
                throw new IllegalArgumentException("Internal manifest identity requires full-property mode");
            }
            if (hasManifestArtifact) {
                parseManifestArtifact(manifestArtifactJson);
            }
        }

        private ExperimentReport runSelectedExperiment(Path effectiveModelDirectory, SandboxInput input)
                throws Exception {
            Path currentApplication = applicationJar();
            if (oracleMode == OracleMode.STATE_INVARIANTS) {
                ExperimentRunner experiment = new ExperimentRunner(
                        TlcRunner.forCurrentJava(currentApplication, toolsJar, maximumStates, maximumStateBytes),
                        Duration.ofSeconds(timeoutSeconds));
                return runExperiment(experiment, effectiveModelDirectory, module, config, workDirectory, input);
            }
            BoundedFileSnapshot moduleSnapshot =
                    BoundedFileSnapshot.read(effectiveModelDirectory.resolve(module), MAXIMUM_MODULE_BYTES);
            BoundedFileSnapshot configSnapshot =
                    BoundedFileSnapshot.read(effectiveModelDirectory.resolve(config), MAXIMUM_CONFIG_BYTES);
            BoundedFileSnapshot toolsSnapshot = BoundedFileSnapshot.read(toolsJar, MAXIMUM_TOOLS_BYTES);
            BoundedFileSnapshot applicationSnapshot =
                    BoundedFileSnapshot.read(currentApplication, MAXIMUM_APPLICATION_BYTES);
            ExperimentInputIdentity.Artifact manifestArtifact = manifestArtifactJson == null
                    ? ExperimentInputIdentity.Artifact.from(
                            BoundedFileSnapshot.read(manifestPath, MAXIMUM_MANIFEST_BYTES))
                    : parseManifestArtifact(manifestArtifactJson);
            ExperimentInputIdentity inputs = new ExperimentInputIdentity(
                    ExperimentInputIdentity.Artifact.from(moduleSnapshot),
                    ExperimentInputIdentity.Artifact.from(configSnapshot),
                    manifestArtifact,
                    ExperimentInputIdentity.Artifact.from(toolsSnapshot),
                    ExperimentInputIdentity.Artifact.from(applicationSnapshot));
            try (TemporaryFileSnapshot frozenApplication =
                            TemporaryFileSnapshot.create(applicationSnapshot, "application.jar");
                    TemporaryFileSnapshot frozenTools = TemporaryFileSnapshot.create(toolsSnapshot, "tla2tools.jar")) {
                ExperimentRunner experiment = new ExperimentRunner(
                        TlcRunner.forCurrentJava(
                                frozenApplication.path(), frozenTools.path(), maximumStates, maximumStateBytes),
                        Duration.ofSeconds(timeoutSeconds));
                return experiment.runFullProperty(effectiveModelDirectory, module, config, workDirectory, inputs);
            }
        }

        private static ExperimentInputIdentity.Artifact parseManifestArtifact(String json) {
            if (json == null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096) {
                throw new IllegalArgumentException("Manifest artifact JSON is too large");
            }
            try {
                return StrictJson.parse(new ObjectMapper(), json, ExperimentInputIdentity.Artifact.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalArgumentException("Manifest artifact JSON is invalid", exception);
            }
        }
    }

    static final class OracleModeConverter implements CommandLine.ITypeConverter<OracleMode> {
        @Override
        public OracleMode convert(String value) {
            return OracleMode.parse(value);
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
