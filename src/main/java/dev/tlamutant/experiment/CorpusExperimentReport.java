package dev.tlamutant.experiment;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        List<CorpusEntryResult> entries,
        @JsonInclude(JsonInclude.Include.NON_NULL) String oracleMode,
        @JsonInclude(JsonInclude.Include.NON_NULL) CorpusExperimentInputIdentity inputs) {
    public CorpusExperimentReport {
        entries = List.copyOf(entries);
        List<ExperimentReport> completed = entries.stream()
                .filter(entry -> entry.status().equals("COMPLETED"))
                .map(CorpusEntryResult::report)
                .toList();
        if (entryCount != entries.size()
                || completedEntries != completed.size()
                || entryCount < 0
                || completedEntries < 0
                || entries.stream().map(CorpusEntryResult::id).distinct().count() != entries.size()
                || generatedMutants != total(completed, ExperimentReport::generatedMutants)
                || evaluatedMutants != total(completed, ExperimentReport::evaluatedMutants)
                || gapCandidates != total(completed, ExperimentReport::gapCandidates)) {
            throw new IllegalArgumentException("Corpus report totals or entry identities are inconsistent");
        }
        if (schemaVersion == 2 && (!OracleMode.FULL_PROPERTY.cliName().equals(oracleMode) || inputs == null)) {
            throw new IllegalArgumentException("Full-property corpus reports require mode and inputs");
        }
        if (schemaVersion == 2) {
            for (CorpusEntryResult entry : entries) {
                if (entry.status().equals("COMPLETED") && !matchesCommonInputs(entry.report(), inputs)) {
                    throw new IllegalArgumentException(
                            "Full-property corpus entry does not match the common input identities");
                }
            }
        }
        if (schemaVersion != 2 && (oracleMode != null || inputs != null)) {
            throw new IllegalArgumentException("Legacy corpus reports cannot contain full-property inputs");
        }
    }

    public CorpusExperimentReport(
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
        this(
                schemaVersion,
                repository,
                commit,
                reviewStatus,
                entryCount,
                completedEntries,
                generatedMutants,
                evaluatedMutants,
                gapCandidates,
                entries,
                null,
                null);
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
                copy,
                null,
                null);
    }

    public static CorpusExperimentReport create(
            String repository,
            String commit,
            OracleMode mode,
            CorpusExperimentInputIdentity inputs,
            List<CorpusEntryResult> entries) {
        if (mode != OracleMode.FULL_PROPERTY) {
            throw new IllegalArgumentException("Common corpus inputs are reserved for full-property mode");
        }
        List<CorpusEntryResult> copy = List.copyOf(entries);
        List<ExperimentReport> completed = copy.stream()
                .filter(entry -> entry.status().equals("COMPLETED"))
                .map(CorpusEntryResult::report)
                .toList();
        return new CorpusExperimentReport(
                2,
                repository,
                commit,
                "proxy-agent-review; human-review-pending",
                copy.size(),
                completed.size(),
                completed.stream().mapToInt(ExperimentReport::generatedMutants).sum(),
                completed.stream().mapToInt(ExperimentReport::evaluatedMutants).sum(),
                completed.stream().mapToInt(ExperimentReport::gapCandidates).sum(),
                copy,
                mode.cliName(),
                inputs);
    }

    private static boolean matchesCommonInputs(ExperimentReport report, CorpusExperimentInputIdentity common) {
        return report != null
                && report.schemaVersion() == 3
                && OracleMode.FULL_PROPERTY.cliName().equals(report.oracleMode())
                && report.inputs() != null
                && report.inputs().manifest().equals(common.manifest())
                && report.inputs().tools().equals(common.tools())
                && report.inputs().application().equals(common.application());
    }

    private static int total(List<ExperimentReport> reports, java.util.function.ToIntFunction<ExperimentReport> value) {
        int total = 0;
        for (ExperimentReport report : reports) {
            total = Math.addExact(total, value.applyAsInt(report));
        }
        return total;
    }
}
