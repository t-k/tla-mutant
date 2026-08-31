package dev.tlamutant.experiment;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

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
    public ExperimentReport {
        results = List.copyOf(results);
        if (schemaVersion == 3
                && (!OracleMode.FULL_PROPERTY.cliName().equals(oracleMode)
                        || inputs == null
                        || results.stream().anyMatch(result -> result.evidence() == null))) {
            throw new IllegalArgumentException("Full-property reports require mode, inputs, and mutation evidence");
        }
        if (schemaVersion != 3 && (oracleMode != null || inputs != null)) {
            throw new IllegalArgumentException("Legacy experiment reports cannot contain full-property evidence");
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
}
