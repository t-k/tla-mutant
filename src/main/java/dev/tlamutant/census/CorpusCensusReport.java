package dev.tlamutant.census;

import java.util.List;
import java.util.Objects;

public record CorpusCensusReport(
        int schemaVersion, String profile, String repository, String commit, List<CorpusCensusEntryResult> entries) {
    public CorpusCensusReport {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported corpus census schema");
        }
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commit, "commit");
        entries = List.copyOf(entries);
    }

    public static CorpusCensusReport create(
            String profile, String repository, String commit, List<CorpusCensusEntryResult> entries) {
        return new CorpusCensusReport(1, profile, repository, commit, entries);
    }
}
