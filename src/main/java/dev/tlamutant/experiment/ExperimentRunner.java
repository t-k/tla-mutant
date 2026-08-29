package dev.tlamutant.experiment;

import dev.tlamutant.mutation.BrlMutator;
import dev.tlamutant.mutation.GcdMutator;
import dev.tlamutant.mutation.MutationCandidate;
import dev.tlamutant.mutation.SourceDocument;
import dev.tlamutant.mutation.SourcePatcher;
import dev.tlamutant.mutation.UoiMutator;
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
        try {
            if (stateInvariants.isEmpty()) {
                StateInvariantConfig.write(modelRoot.resolve(configFile), stateInvariantConfig);
            } else {
                StateInvariantConfig.write(modelRoot.resolve(configFile), stateInvariantConfig, stateInvariants);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create the State Invariant-only configuration", exception);
        }
        List<MutationCandidate> candidates = new ArrayList<>();
        candidates.addAll(new GcdMutator().generate(project, source));
        candidates.addAll(new BrlMutator().generate(project, source));
        candidates.addAll(new UoiMutator().generate(project, source));
        candidates.sort(Comparator.comparing(MutationCandidate::id));

        TlcExecution baseline = tlcRunner.run(
                modelRoot,
                moduleFile,
                stateInvariantConfig.toString(),
                results.resolve("baseline-states.jsonl"),
                timeout);
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

        List<MutationResult> mutationResults = new ArrayList<>();
        for (MutationCandidate candidate : candidates) {
            mutationResults.add(runCandidate(
                    modelRoot, moduleFile, configFile, stateInvariantConfig, results, source, baseline, candidate));
        }
        int evaluated = (int) mutationResults.stream()
                .filter(result -> result.executionStatus().equals(ExecutionStatus.COMPLETED.name()))
                .count();
        int gaps = (int) mutationResults.stream()
                .filter(result -> result.verdict().equals("state-invariant-gap-candidate"))
                .count();
        return new ExperimentReport(
                1, moduleFile, configFile, "proxy-agent-review", candidates.size(), evaluated, gaps, mutationResults);
    }

    private static String bounded(String value) {
        String text = value == null ? "" : value;
        return text.substring(0, Math.min(text.length(), 4000));
    }

    private MutationResult runCandidate(
            Path modelRoot,
            String moduleFile,
            String configFile,
            Path stateInvariantConfig,
            Path results,
            SourceDocument source,
            TlcExecution baseline,
            MutationCandidate candidate) {
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
            return classify(candidate, baseline, mutant);
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

    private MutationResult classify(MutationCandidate candidate, TlcExecution baseline, TlcExecution mutant) {
        String relationName = "STATE_UNRESOLVED";
        String verdict;
        String witness = null;
        String diagnosticReason = null;
        if (mutant.status() == ExecutionStatus.COMPLETED && mutant.oracle() == OracleOutcome.KILLED_STATE_SAFETY) {
            verdict = "state-invariant-kill-unattributed";
            diagnosticReason = "configured-state-invariant-violation; type attribution not run";
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
                witness);
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
