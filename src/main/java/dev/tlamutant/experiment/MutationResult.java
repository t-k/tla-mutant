package dev.tlamutant.experiment;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        KillAttribution attribution,
        @JsonInclude(JsonInclude.Include.NON_NULL) MutationEvidence evidence) {
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
            String mutantOnlyWitness,
            KillAttribution attribution) {
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
                attribution,
                null);
    }

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
                null,
                null);
    }
}
