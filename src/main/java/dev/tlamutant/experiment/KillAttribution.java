package dev.tlamutant.experiment;

import dev.tlamutant.state.StateRelationAnalyzer;
import dev.tlamutant.tlc.ExecutionStatus;
import dev.tlamutant.tlc.OracleOutcome;
import dev.tlamutant.tlc.TlcExecution;

public record KillAttribution(
        String category,
        String typeOnlyExecutionStatus,
        String typeOnlyOracleOutcome,
        Integer typeOnlyStateCount,
        String legacyExecutionStatus,
        String legacyOracleOutcome,
        String legacyStateRelation,
        Integer legacyStateCount,
        String legacyMutantOnlyWitness,
        String diagnosticReason) {
    public static KillAttribution classify(
            InvariantOracleSets sets,
            TlcExecution typeOnly,
            TlcExecution legacy,
            StateRelationAnalyzer.Analysis legacyRelation) {
        String category;
        String diagnostic = null;
        if (sets.full().equals(sets.typeInvariants())) {
            category = "type-invariant-kill";
        } else if (requiresTypeEvidence(sets)) {
            if (isKill(typeOnly)) {
                category = "type-invariant-kill";
            } else if (!isPass(typeOnly)) {
                category = "kill-attribution-unresolved";
                diagnostic = evidenceFailure("type-only", typeOnly);
            } else {
                category = classifyTargetEvidence(sets, legacy);
                if (category.equals("kill-attribution-unresolved")) {
                    diagnostic = evidenceFailure("legacy", legacy);
                }
            }
        } else {
            category = classifyTargetEvidence(sets, legacy);
            if (category.equals("kill-attribution-unresolved")) {
                diagnostic = evidenceFailure("legacy", legacy);
            }
        }
        return new KillAttribution(
                category,
                status(typeOnly),
                oracle(typeOnly),
                stateCount(typeOnly),
                status(legacy),
                oracle(legacy),
                legacyRelation == null ? null : legacyRelation.relation().name(),
                stateCount(legacy),
                legacyRelation == null
                        ? null
                        : legacyRelation
                                .mutantOnlyWitness()
                                .map(dev.tlamutant.state.CanonicalState::canonicalValue)
                                .orElse(null),
                diagnostic);
    }

    private static String classifyTargetEvidence(InvariantOracleSets sets, TlcExecution legacy) {
        if (sets.adoptedTargetInvariants().isEmpty()) {
            return "legacy-target-invariant-kill";
        }
        if (sets.legacyTargetInvariants().isEmpty()) {
            return "adopted-target-invariant-kill";
        }
        if (isPass(legacy)) {
            return "adopted-target-invariant-kill";
        }
        if (isKill(legacy)) {
            return "legacy-target-invariant-kill";
        }
        return "kill-attribution-unresolved";
    }

    private static boolean requiresTypeEvidence(InvariantOracleSets sets) {
        return !sets.typeInvariants().isEmpty() && !sets.full().equals(sets.typeInvariants());
    }

    private static boolean isPass(TlcExecution execution) {
        return execution != null
                && execution.status() == ExecutionStatus.COMPLETED
                && execution.oracle() == OracleOutcome.PASSED;
    }

    private static boolean isKill(TlcExecution execution) {
        return execution != null
                && execution.status() == ExecutionStatus.COMPLETED
                && execution.oracle() == OracleOutcome.KILLED_STATE_SAFETY;
    }

    private static String evidenceFailure(String oracleName, TlcExecution execution) {
        if (execution == null) {
            return oracleName + " attribution evidence was not produced";
        }
        return oracleName + " attribution evidence was " + execution.status() + "/" + execution.oracle();
    }

    private static String status(TlcExecution execution) {
        return execution == null ? null : execution.status().name();
    }

    private static String oracle(TlcExecution execution) {
        return execution == null ? null : execution.oracle().name();
    }

    private static Integer stateCount(TlcExecution execution) {
        return execution == null ? null : execution.states().size();
    }
}
