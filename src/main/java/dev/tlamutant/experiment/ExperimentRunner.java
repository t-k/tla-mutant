package dev.tlamutant.experiment;

import dev.tlamutant.mutation.BrlMutator;
import dev.tlamutant.mutation.GcdMutator;
import dev.tlamutant.mutation.MutationCandidate;
import dev.tlamutant.mutation.SourceDocument;
import dev.tlamutant.mutation.SourcePatcher;
import dev.tlamutant.mutation.UoiMutator;
import dev.tlamutant.security.BoundedFileSnapshot;
import dev.tlamutant.semantic.SanyAnalyzer;
import dev.tlamutant.semantic.SemanticProject;
import dev.tlamutant.state.StateRelation;
import dev.tlamutant.state.StateRelationAnalyzer;
import dev.tlamutant.tlc.ExecutionStatus;
import dev.tlamutant.tlc.OracleOutcome;
import dev.tlamutant.tlc.TlcExecution;
import dev.tlamutant.tlc.TlcRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ExperimentRunner {
    private static final long MAXIMUM_MODULE_BYTES = 64L * 1024 * 1024;
    private final TlcRunner tlcRunner;
    private final Duration timeout;
    private final SanyAnalyzer sanyAnalyzer = new SanyAnalyzer();
    private final StateRelationAnalyzer relationAnalyzer = new StateRelationAnalyzer();

    public ExperimentRunner(TlcRunner tlcRunner, Duration timeout) {
        this.tlcRunner = Objects.requireNonNull(tlcRunner, "tlcRunner");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public ExperimentReport run(Path modelDirectory, String moduleFile, String configFile, Path resultDirectory) {
        return run(modelDirectory, moduleFile, configFile, resultDirectory, List.of());
    }

    public ExperimentReport run(
            Path modelDirectory,
            String moduleFile,
            String configFile,
            Path resultDirectory,
            List<String> stateInvariants) {
        return runInternal(modelDirectory, moduleFile, configFile, resultDirectory, stateInvariants, null);
    }

    public ExperimentReport run(
            Path modelDirectory,
            String moduleFile,
            String configFile,
            Path resultDirectory,
            InvariantOracleSets oracleSets) {
        return runInternal(modelDirectory, moduleFile, configFile, resultDirectory, oracleSets.full(), oracleSets);
    }

    public ExperimentReport runFullProperty(
            Path modelDirectory,
            String moduleFile,
            String configFile,
            Path resultDirectory,
            ExperimentInputIdentity inputs) {
        Objects.requireNonNull(inputs, "inputs");
        Path modelRoot = modelDirectory.toAbsolutePath().normalize();
        Path results = resultDirectory.toAbsolutePath().normalize();
        Path frozenModel = null;
        try {
            Files.createDirectories(results);
            frozenModel = Files.createTempDirectory(results, ".full-property-model-");
            copyModel(modelRoot, frozenModel);
            SourceDocument source = SourceDocument.read(frozenModel.resolve(moduleFile));
            BoundedFileSnapshot moduleSnapshot =
                    BoundedFileSnapshot.read(frozenModel.resolve(moduleFile), MAXIMUM_MODULE_BYTES);
            FullPropertyConfig.Materialized config =
                    FullPropertyConfig.write(frozenModel.resolve(configFile), results.resolve("full-properties.cfg"));
            validateFullPropertyInputs(inputs, moduleSnapshot, config.snapshot());
            SemanticProject project = sanyAnalyzer.analyze(frozenModel, moduleFile, configFile);
            List<MutationCandidate> candidates = candidates(project, source);

            TlcExecution baseline = tlcRunner.run(
                    frozenModel,
                    moduleFile,
                    config.path().toString(),
                    results.resolve("baseline-states.jsonl"),
                    timeout);
            requirePassingBaseline(baseline);

            List<MutationResult> mutationResults = new ArrayList<>();
            for (MutationCandidate candidate : candidates) {
                mutationResults.add(runFullPropertyCandidate(
                        frozenModel, moduleFile, configFile, config.path(), results, source, baseline, candidate));
            }
            int evaluated = (int) mutationResults.stream()
                    .filter(result -> result.executionStatus().equals(ExecutionStatus.COMPLETED.name()))
                    .count();
            int survivors = (int) mutationResults.stream()
                    .filter(result -> result.verdict().equals("full-property-survivor"))
                    .count();
            return new ExperimentReport(
                    3,
                    moduleFile,
                    configFile,
                    "proxy-agent-review",
                    candidates.size(),
                    evaluated,
                    survivors,
                    mutationResults,
                    OracleMode.FULL_PROPERTY.cliName(),
                    inputs);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create the full-property experiment inputs", exception);
        } finally {
            deleteTree(frozenModel);
        }
    }

    private ExperimentReport runInternal(
            Path modelDirectory,
            String moduleFile,
            String configFile,
            Path resultDirectory,
            List<String> stateInvariants,
            InvariantOracleSets oracleSets) {
        Path modelRoot = modelDirectory.toAbsolutePath().normalize();
        Path results = resultDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(results);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create experiment result directory", exception);
        }
        SemanticProject project = sanyAnalyzer.analyze(modelRoot, moduleFile, configFile);
        SourceDocument source;
        try {
            source = SourceDocument.read(modelRoot.resolve(moduleFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read root module", exception);
        }
        Path stateInvariantConfig = results.resolve("state-invariants.cfg");
        Path typeOnlyConfig = oracleSets != null && oracleSets.needsTypeOnlyRerun()
                ? results.resolve("type-only-invariants.cfg")
                : null;
        Path legacyConfig =
                oracleSets != null && oracleSets.needsLegacyRerun() ? results.resolve("legacy-invariants.cfg") : null;
        try {
            if (stateInvariants.isEmpty()) {
                StateInvariantConfig.write(modelRoot.resolve(configFile), stateInvariantConfig);
            } else {
                StateInvariantConfig.write(modelRoot.resolve(configFile), stateInvariantConfig, stateInvariants);
            }
            if (typeOnlyConfig != null) {
                StateInvariantConfig.write(modelRoot.resolve(configFile), typeOnlyConfig, oracleSets.typeInvariants());
            }
            if (legacyConfig != null) {
                StateInvariantConfig.write(modelRoot.resolve(configFile), legacyConfig, oracleSets.legacy());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create the State Invariant-only configuration", exception);
        }
        List<MutationCandidate> candidates = candidates(project, source);

        TlcExecution baseline = tlcRunner.run(
                modelRoot,
                moduleFile,
                stateInvariantConfig.toString(),
                results.resolve("baseline-states.jsonl"),
                timeout);
        requirePassingBaseline(baseline);

        List<MutationResult> mutationResults = new ArrayList<>();
        for (MutationCandidate candidate : candidates) {
            mutationResults.add(runCandidate(
                    modelRoot,
                    moduleFile,
                    configFile,
                    stateInvariantConfig,
                    typeOnlyConfig,
                    legacyConfig,
                    results,
                    source,
                    baseline,
                    candidate,
                    oracleSets));
        }
        int evaluated = (int) mutationResults.stream()
                .filter(result -> result.executionStatus().equals(ExecutionStatus.COMPLETED.name()))
                .count();
        int gaps = (int) mutationResults.stream()
                .filter(result -> result.verdict().equals("state-invariant-gap-candidate"))
                .count();
        return new ExperimentReport(
                oracleSets == null ? 1 : 2,
                moduleFile,
                configFile,
                "proxy-agent-review",
                candidates.size(),
                evaluated,
                gaps,
                mutationResults);
    }

    private static String bounded(String value) {
        String text = value == null ? "" : value;
        return text.substring(0, Math.min(text.length(), 4000));
    }

    private static List<MutationCandidate> candidates(SemanticProject project, SourceDocument source) {
        List<MutationCandidate> candidates = new ArrayList<>();
        candidates.addAll(new GcdMutator().generate(project, source));
        candidates.addAll(new BrlMutator().generate(project, source));
        candidates.addAll(new UoiMutator().generate(project, source));
        candidates.sort(Comparator.comparing(MutationCandidate::id));
        return List.copyOf(candidates);
    }

    private static void requirePassingBaseline(TlcExecution baseline) {
        if (baseline.status() != ExecutionStatus.COMPLETED || baseline.oracle() != OracleOutcome.PASSED) {
            throw new IllegalStateException("Baseline did not complete successfully: "
                    + baseline.status()
                    + "/"
                    + baseline.oracle()
                    + "\nstdout:\n"
                    + bounded(baseline.stdout())
                    + "\nstderr:\n"
                    + bounded(baseline.stderr()));
        }
    }

    private static void validateFullPropertyInputs(
            ExperimentInputIdentity inputs, BoundedFileSnapshot module, BoundedFileSnapshot config) {
        if (!inputs.module().equals(ExperimentInputIdentity.Artifact.from(module))
                || !inputs.config().equals(ExperimentInputIdentity.Artifact.from(config))) {
            throw new IllegalArgumentException("Full-property module or config identity does not match frozen input");
        }
    }

    private MutationResult runFullPropertyCandidate(
            Path modelRoot,
            String moduleFile,
            String configFile,
            Path fullPropertyConfig,
            Path results,
            SourceDocument source,
            TlcExecution baseline,
            MutationCandidate candidate) {
        Path workspace = null;
        SourceDocument patched = new SourcePatcher().apply(source, candidate);
        TlcExecution mutant;
        try {
            workspace = Files.createTempDirectory(results, ".mutant-" + candidate.id() + "-");
            copyModel(modelRoot, workspace);
            Files.writeString(workspace.resolve(moduleFile), patched.content());
            sanyAnalyzer.analyze(workspace, moduleFile, configFile);
            mutant = tlcRunner.run(
                    workspace,
                    moduleFile,
                    fullPropertyConfig.toString(),
                    results.resolve(candidate.id() + "-states.jsonl"),
                    timeout);
        } catch (IllegalArgumentException | IOException exception) {
            mutant = new TlcExecution(
                    ExecutionStatus.INVALID_SEMANTIC,
                    OracleOutcome.NOT_EVALUATED,
                    -1,
                    List.of(),
                    List.of(),
                    "",
                    exception.getClass().getSimpleName(),
                    false);
        } finally {
            deleteTree(workspace);
        }
        return classifyFullProperty(candidate, source, patched, baseline, mutant);
    }

    private MutationResult classifyFullProperty(
            MutationCandidate candidate,
            SourceDocument source,
            SourceDocument patched,
            TlcExecution baseline,
            TlcExecution mutant) {
        String relationName = "STATE_UNRESOLVED";
        String verdict = "unresolved";
        String witness = null;
        String diagnosticReason = null;
        if (mutant.status() == ExecutionStatus.COMPLETED) {
            switch (mutant.oracle()) {
                case KILLED_STATE_SAFETY -> verdict = "full-property-state-safety-kill";
                case KILLED_TEMPORAL_NAMED, KILLED_TEMPORAL_GENERIC -> verdict = "full-property-temporal-kill";
                case KILLED_DEADLOCK -> verdict = "full-property-deadlock-kill";
                case PASSED -> {
                    StateRelationAnalyzer.Analysis relation =
                            relationAnalyzer.analyze(baseline.states(), mutant.states());
                    relationName = relation.relation().name();
                    witness = relation.mutantOnlyWitness()
                            .map(dev.tlamutant.state.CanonicalState::canonicalValue)
                            .orElse(null);
                    verdict = "full-property-survivor";
                }
                case NOT_EVALUATED -> {
                    verdict = "unresolved";
                    diagnosticReason = diagnosticReason(mutant);
                }
            }
        } else {
            verdict = "unresolved";
            diagnosticReason = diagnosticReason(mutant);
        }
        MutationEvidence evidence = MutationEvidence.create(candidate, source, patched, mutant);
        return new MutationResult(
                candidate.id(),
                candidate.family().name(),
                candidate.definition(),
                candidate.original(),
                candidate.replacement(),
                mutant.status().name(),
                mutant.oracle().name(),
                relationName,
                verdict,
                diagnosticReason,
                baseline.states().size(),
                mutant.states().size(),
                witness,
                null,
                evidence);
    }

    private MutationResult runCandidate(
            Path modelRoot,
            String moduleFile,
            String configFile,
            Path stateInvariantConfig,
            Path typeOnlyConfig,
            Path legacyConfig,
            Path results,
            SourceDocument source,
            TlcExecution baseline,
            MutationCandidate candidate,
            InvariantOracleSets oracleSets) {
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory(results, ".mutant-" + candidate.id() + "-");
            copyModel(modelRoot, workspace);
            SourceDocument patched = new SourcePatcher().apply(source, candidate);
            Files.writeString(workspace.resolve(moduleFile), patched.content());
            sanyAnalyzer.analyze(workspace, moduleFile, configFile);
            TlcExecution mutant = tlcRunner.run(
                    workspace,
                    moduleFile,
                    stateInvariantConfig.toString(),
                    results.resolve(candidate.id() + "-states.jsonl"),
                    timeout);
            TlcExecution typeOnly = null;
            TlcExecution legacy = null;
            StateRelationAnalyzer.Analysis typeOnlyRelation = null;
            StateRelationAnalyzer.Analysis legacyRelation = null;
            if (oracleSets != null
                    && mutant.status() == ExecutionStatus.COMPLETED
                    && mutant.oracle() == OracleOutcome.KILLED_STATE_SAFETY) {
                if (oracleSets.needsTypeOnlyRerun()) {
                    typeOnly = tlcRunner.run(
                            workspace,
                            moduleFile,
                            typeOnlyConfig.toString(),
                            results.resolve(candidate.id() + "-type-only-states.jsonl"),
                            timeout);
                    if (typeOnly.status() == ExecutionStatus.COMPLETED && typeOnly.oracle() == OracleOutcome.PASSED) {
                        typeOnlyRelation = relationAnalyzer.analyze(baseline.states(), typeOnly.states());
                    }
                }
                boolean typeAllowsTargetAttribution = typeOnly == null
                        || (typeOnly.status() == ExecutionStatus.COMPLETED
                                && typeOnly.oracle() == OracleOutcome.PASSED);
                if (typeAllowsTargetAttribution && oracleSets.needsLegacyRerun()) {
                    legacy = tlcRunner.run(
                            workspace,
                            moduleFile,
                            legacyConfig.toString(),
                            results.resolve(candidate.id() + "-legacy-states.jsonl"),
                            timeout);
                    if (legacy.status() == ExecutionStatus.COMPLETED && legacy.oracle() == OracleOutcome.PASSED) {
                        legacyRelation = relationAnalyzer.analyze(baseline.states(), legacy.states());
                    }
                }
            }
            return classify(
                    candidate, baseline, mutant, oracleSets, typeOnly, typeOnlyRelation, legacy, legacyRelation);
        } catch (IllegalArgumentException | IOException exception) {
            return new MutationResult(
                    candidate.id(),
                    candidate.family().name(),
                    candidate.definition(),
                    candidate.original(),
                    candidate.replacement(),
                    "INVALID_SEMANTIC",
                    "NOT_EVALUATED",
                    "STATE_UNRESOLVED",
                    "invalid",
                    "post-mutation-sany-rejection",
                    baseline.states().size(),
                    0,
                    null);
        } finally {
            deleteTree(workspace);
        }
    }

    private MutationResult classify(
            MutationCandidate candidate,
            TlcExecution baseline,
            TlcExecution mutant,
            InvariantOracleSets oracleSets,
            TlcExecution typeOnly,
            StateRelationAnalyzer.Analysis typeOnlyRelation,
            TlcExecution legacy,
            StateRelationAnalyzer.Analysis legacyRelation) {
        String relationName = "STATE_UNRESOLVED";
        String verdict;
        String witness = null;
        String diagnosticReason = null;
        KillAttribution attribution = null;
        if (mutant.status() == ExecutionStatus.COMPLETED && mutant.oracle() == OracleOutcome.KILLED_STATE_SAFETY) {
            if (oracleSets == null) {
                verdict = "state-invariant-kill-unattributed";
                diagnosticReason = "configured-state-invariant-violation; type attribution not run";
            } else {
                attribution = KillAttribution.classify(oracleSets, typeOnly, typeOnlyRelation, legacy, legacyRelation);
                verdict = attribution.category();
                diagnosticReason = attribution.diagnosticReason();
            }
        } else if (mutant.status() == ExecutionStatus.COMPLETED && mutant.oracle() == OracleOutcome.PASSED) {
            StateRelationAnalyzer.Analysis relation = relationAnalyzer.analyze(baseline.states(), mutant.states());
            relationName = relation.relation().name();
            witness = relation.mutantOnlyWitness()
                    .map(dev.tlamutant.state.CanonicalState::canonicalValue)
                    .orElse(null);
            verdict = (relation.relation() == StateRelation.STATE_EXPANDING
                            || relation.relation() == StateRelation.STATE_INCOMPARABLE)
                    ? "state-invariant-gap-candidate"
                    : relation.relation().name().toLowerCase().replace('_', '-');
        } else {
            verdict = "unresolved";
            diagnosticReason = diagnosticReason(mutant);
        }
        return new MutationResult(
                candidate.id(),
                candidate.family().name(),
                candidate.definition(),
                candidate.original(),
                candidate.replacement(),
                mutant.status().name(),
                mutant.oracle().name(),
                relationName,
                verdict,
                diagnosticReason,
                baseline.states().size(),
                mutant.states().size(),
                witness,
                attribution);
    }

    private static String diagnosticReason(TlcExecution execution) {
        String codes = execution.events().stream()
                .filter(event -> event.severity() > 0)
                .map(event -> Integer.toString(event.code()))
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        String reason =
                execution.status().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        if (!codes.isEmpty()) {
            reason += "; tool-event-codes=" + codes;
        }
        if (execution.outputTruncated()) {
            reason += "; output-truncated";
        }
        return reason;
    }

    private static void copyModel(Path source, Path destination) throws IOException {
        try (var files = Files.list(source)) {
            for (Path file : files.sorted().toList()) {
                if (Files.isRegularFile(file) && !Files.isSymbolicLink(file)) {
                    try (var input = Files.newInputStream(file)) {
                        Files.copy(input, destination.resolve(file.getFileName().toString()));
                    }
                }
            }
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // The exact temporary mutant workspace is checked again during final leak inspection.
                }
            });
        } catch (IOException ignored) {
            // The exact temporary mutant workspace is checked again during final leak inspection.
        }
    }
}
