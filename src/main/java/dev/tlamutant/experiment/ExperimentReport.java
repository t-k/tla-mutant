package dev.tlamutant.experiment;

import java.util.List;

public record ExperimentReport(
        int schemaVersion,
        String module,
        String config,
        String reviewStatus,
        int generatedMutants,
        int evaluatedMutants,
        int gapCandidates,
        List<MutationResult> results) {
    public ExperimentReport {
        results = List.copyOf(results);
    }
}
