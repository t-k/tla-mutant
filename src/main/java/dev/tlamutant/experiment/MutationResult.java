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
        int baselineStateCount,
        int mutantStateCount,
        String mutantOnlyWitness) {}
