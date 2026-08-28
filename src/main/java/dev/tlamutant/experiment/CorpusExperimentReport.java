package dev.tlamutant.experiment;

import java.util.List;

public record CorpusExperimentReport(
        int schemaVersion,
        String repository,
        String commit,
        String reviewStatus,
        int entryCount,
        int completedEntries,
        int generatedMutants,
        int evaluatedMutants,
        int gapCandidates,
        List<CorpusEntryResult> entries) {
    public CorpusExperimentReport {
        entries = List.copyOf(entries);
    }

    public static CorpusExperimentReport create(String repository, String commit, List<CorpusEntryResult> entries) {
        List<CorpusEntryResult> copy = List.copyOf(entries);
        List<ExperimentReport> completed = copy.stream()
                .filter(entry -> entry.status().equals("COMPLETED"))
                .map(CorpusEntryResult::report)
                .toList();
        return new CorpusExperimentReport(
                1,
                repository,
                commit,
                "proxy-agent-review; human-review-pending",
                copy.size(),
                completed.size(),
                completed.stream().mapToInt(ExperimentReport::generatedMutants).sum(),
                completed.stream().mapToInt(ExperimentReport::evaluatedMutants).sum(),
                completed.stream().mapToInt(ExperimentReport::gapCandidates).sum(),
                copy);
    }
}
