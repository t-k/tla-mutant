package dev.tlamutant.experiment;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.tlamutant.mutation.MutationCandidate;
import dev.tlamutant.mutation.MutationFamily;
import dev.tlamutant.mutation.SourceRange;
import dev.tlamutant.state.StateRelation;
import dev.tlamutant.tlc.ExecutionStatus;
import dev.tlamutant.tlc.OracleOutcome;
import java.util.List;
import java.util.Set;

public record ExperimentReport(
        int schemaVersion,
        String module,
        String config,
        String reviewStatus,
        int generatedMutants,
        int evaluatedMutants,
        int gapCandidates,
        List<MutationResult> results,
        @JsonInclude(JsonInclude.Include.NON_NULL) String oracleMode,
        @JsonInclude(JsonInclude.Include.NON_NULL) ExperimentInputIdentity inputs) {
    private static final Set<Integer> SAFETY_CODES = Set.of(2107, 2108, 2110, 2112);
    private static final Set<Integer> SUPPORT_CODES = Set.of(2121, 2217, 2218, 2264);

    public ExperimentReport {
        results = List.copyOf(results);
        if (schemaVersion == 3
                && (!OracleMode.FULL_PROPERTY.cliName().equals(oracleMode)
                        || inputs == null
                        || inputs.modelFiles().isEmpty()
                        || results.stream().anyMatch(result -> result.evidence() == null))) {
            throw new IllegalArgumentException("Full-property reports require mode, inputs, and mutation evidence");
        }
        if (schemaVersion != 3 && (oracleMode != null || inputs != null)) {
            throw new IllegalArgumentException("Legacy experiment reports cannot contain full-property evidence");
        }
        if (schemaVersion == 3) {
            validateFullPropertyReport(
                    module, config, generatedMutants, evaluatedMutants, gapCandidates, results, inputs);
        }
    }

    public ExperimentReport(
            int schemaVersion,
            String module,
            String config,
            String reviewStatus,
            int generatedMutants,
            int evaluatedMutants,
            int gapCandidates,
            List<MutationResult> results) {
        this(
                schemaVersion,
                module,
                config,
                reviewStatus,
                generatedMutants,
                evaluatedMutants,
                gapCandidates,
                results,
                null,
                null);
    }

    private static void validateFullPropertyReport(
            String module,
            String config,
            int generated,
            int evaluated,
            int survivors,
            List<MutationResult> results,
            ExperimentInputIdentity inputs) {
        if (generated != results.size()
                || evaluated
                        != results.stream()
                                .filter(result ->
                                        ExecutionStatus.COMPLETED.name().equals(result.executionStatus()))
                                .count()
                || survivors
                        != results.stream()
                                .filter(result -> "full-property-survivor".equals(result.verdict()))
                                .count()
                || results.stream().map(MutationResult::id).distinct().count() != results.size()) {
            throw new IllegalArgumentException("Full-property report totals or candidate identities are inconsistent");
        }
        ExperimentInputIdentity.Artifact moduleIdentity = modelIdentity(inputs, module);
        ExperimentInputIdentity.Artifact configIdentity = modelIdentity(inputs, config);
        if (!inputs.module().equals(moduleIdentity) || !inputs.config().equals(configIdentity)) {
            throw new IllegalArgumentException("Full-property report evidence does not match its frozen inputs");
        }
        String moduleName = moduleName(module);
        Integer baselineStateCount = null;
        for (MutationResult result : results) {
            validateFullPropertyResult(result, moduleName, inputs.module());
            if (baselineStateCount == null) {
                baselineStateCount = result.baselineStateCount();
            } else if (baselineStateCount != result.baselineStateCount()) {
                throw new IllegalArgumentException("Full-property results disagree on the baseline state count");
            }
        }
    }

    private static void validateFullPropertyResult(
            MutationResult result, String moduleName, ExperimentInputIdentity.Artifact moduleIdentity) {
        MutationEvidence evidence = result.evidence();
        MutationEvidence.Patch patch = evidence.patch();
        MutationFamily family = enumValue(MutationFamily.class, result.family(), "mutation family");
        ExecutionStatus status = enumValue(ExecutionStatus.class, result.executionStatus(), "execution status");
        OracleOutcome oracle = enumValue(OracleOutcome.class, result.oracleOutcome(), "oracle outcome");
        StateRelation relation = enumValue(StateRelation.class, result.stateRelation(), "state relation");
        SourceRange range = new SourceRange(
                evidence.range().startUtf16Offset(), evidence.range().endUtf16OffsetExclusive());
        MutationCandidate candidate = MutationCandidate.create(
                family, evidence.module(), result.definition(), range, result.original(), result.replacement());
        if (!candidate.id().equals(result.id())
                || !candidate.module().equals(moduleName)
                || !evidence.module().equals(moduleName)
                || !evidence.definition().equals(result.definition())
                || !evidence.sourceSha256().equals(moduleIdentity.sha256())
                || !patch.module().equals(evidence.module())
                || !patch.sourceSha256().equals(evidence.sourceSha256())
                || !patch.mutatedSourceSha256().equals(evidence.mutatedSourceSha256())
                || patch.startUtf16Offset() != evidence.range().startUtf16Offset()
                || patch.endUtf16OffsetExclusive() != evidence.range().endUtf16OffsetExclusive()
                || !patch.expected().equals(result.original())
                || !patch.replacement().equals(result.replacement())
                || result.baselineStateCount() < 0
                || result.mutantStateCount() < 0
                || result.attribution() != null) {
            throw new IllegalArgumentException("Full-property candidate or evidence identity is inconsistent");
        }
        if (status == ExecutionStatus.COMPLETED) {
            validateCompletedResult(result, oracle, relation, evidence);
        } else if (oracle != OracleOutcome.NOT_EVALUATED
                || relation != StateRelation.STATE_UNRESOLVED
                || !"unresolved".equals(result.verdict())
                || result.diagnosticReason() == null
                || result.diagnosticReason().isBlank()
                || result.mutantOnlyWitness() != null
                || evidence.violatedProperty() != null) {
            throw new IllegalArgumentException("Non-completed full-property result has contradictory evidence");
        }
    }

    private static void validateCompletedResult(
            MutationResult result, OracleOutcome oracle, StateRelation relation, MutationEvidence evidence) {
        if (result.diagnosticReason() != null) {
            throw new IllegalArgumentException("Completed full-property result cannot contain a diagnostic reason");
        }
        switch (oracle) {
            case PASSED -> {
                if (!"full-property-survivor".equals(result.verdict())
                        || relation == StateRelation.STATE_UNRESOLVED
                        || evidence.tlcExitCode() != 0
                        || !evidence.toolEventCodes().isEmpty()
                        || evidence.violatedProperty() != null
                        || witnessMismatch(result, relation)) {
                    throw new IllegalArgumentException("Full-property survivor evidence is inconsistent");
                }
            }
            case KILLED_STATE_SAFETY ->
                validateKill(result, relation, evidence, "full-property-state-safety-kill", SAFETY_CODES, false);
            case KILLED_TEMPORAL_NAMED ->
                validateKill(result, relation, evidence, "full-property-temporal-kill", Set.of(2116), true);
            case KILLED_TEMPORAL_GENERIC ->
                validateKill(result, relation, evidence, "full-property-temporal-kill", Set.of(2116), false);
            case KILLED_DEADLOCK ->
                validateKill(result, relation, evidence, "full-property-deadlock-kill", Set.of(2114), false);
            case NOT_EVALUATED ->
                throw new IllegalArgumentException("Completed full-property result cannot have a non-evaluated oracle");
        }
    }

    private static void validateKill(
            MutationResult result,
            StateRelation relation,
            MutationEvidence evidence,
            String verdict,
            Set<Integer> terminalCodes,
            boolean requiresProperty) {
        long terminals = evidence.toolEventCodes().stream()
                .filter(terminalCodes::contains)
                .count();
        boolean unsafeCode = evidence.toolEventCodes().stream()
                .anyMatch(code -> !terminalCodes.contains(code) && !SUPPORT_CODES.contains(code));
        boolean propertyMismatch = requiresProperty
                ? evidence.violatedProperty() == null
                : evidence.violatedProperty() != null && !SAFETY_CODES.equals(terminalCodes);
        if (!verdict.equals(result.verdict())
                || relation != StateRelation.STATE_UNRESOLVED
                || result.mutantOnlyWitness() != null
                || evidence.tlcExitCode() == 0
                || terminals != 1
                || unsafeCode
                || propertyMismatch) {
            throw new IllegalArgumentException("Full-property kill evidence is inconsistent");
        }
    }

    private static boolean witnessMismatch(MutationResult result, StateRelation relation) {
        boolean requiresWitness =
                relation == StateRelation.STATE_EXPANDING || relation == StateRelation.STATE_INCOMPARABLE;
        return requiresWitness != (result.mutantOnlyWitness() != null);
    }

    private static String moduleName(String module) {
        int slash = module.lastIndexOf('/');
        String fileName = module.substring(slash + 1);
        if (!fileName.endsWith(".tla") || fileName.length() == 4) {
            throw new IllegalArgumentException("Full-property report module path is invalid");
        }
        return fileName.substring(0, fileName.length() - 4);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Full-property " + label + " is invalid", exception);
        }
    }

    private static ExperimentInputIdentity.Artifact modelIdentity(ExperimentInputIdentity inputs, String path) {
        return inputs.modelFiles().stream()
                .filter(file -> file.path().equals(path))
                .map(ExperimentInputIdentity.ModelFile::identity)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Full-property report omits an executed model file"));
    }
}
