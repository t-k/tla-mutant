package dev.tlamutant.experiment;

public record MutationResult(
        String id,
        String family,
        String definition,
        String original,
        String replacement,
        String executionStatus,
        String oracleOutcome,
        String stateRelation,
        String verdict,
        String diagnosticReason,
        int baselineStateCount,
        int mutantStateCount,
        String mutantOnlyWitness,
        KillAttribution attribution) {
    public MutationResult(
            String id,
            String family,
            String definition,
            String original,
            String replacement,
            String executionStatus,
            String oracleOutcome,
            String stateRelation,
            String verdict,
            String diagnosticReason,
            int baselineStateCount,
            int mutantStateCount,
            String mutantOnlyWitness) {
        this(
                id,
                family,
                definition,
                original,
                replacement,
                executionStatus,
                oracleOutcome,
                stateRelation,
                verdict,
                diagnosticReason,
                baselineStateCount,
                mutantStateCount,
                mutantOnlyWitness,
                null);
    }
}
