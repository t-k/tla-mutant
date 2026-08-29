package dev.tlamutant.census;

import java.util.List;
import java.util.Objects;

public record CorpusCensusReport(
        int schemaVersion,
        String profile,
        String repository,
        String commit,
        CensusInputIdentity inputs,
        List<CorpusCensusEntryResult> entries) {
    public CorpusCensusReport {
        if ((schemaVersion != 1 && schemaVersion != 2 && schemaVersion != 3)
                || (schemaVersion == 1 && inputs != null)
                || (schemaVersion == 2 && (inputs == null || inputs.application() != null))
                || (schemaVersion == 3 && (inputs == null || inputs.application() == null))) {
            throw new IllegalArgumentException("Unsupported corpus census schema");
        }
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commit, "commit");
        entries = List.copyOf(entries);
    }

    public CorpusCensusReport(
            int schemaVersion,
            String profile,
            String repository,
            String commit,
            List<CorpusCensusEntryResult> entries) {
        this(schemaVersion, profile, repository, commit, null, entries);
    }

    public static CorpusCensusReport create(
            String profile,
            String repository,
            String commit,
            CensusInputIdentity inputs,
            List<CorpusCensusEntryResult> entries) {
        return new CorpusCensusReport(3, profile, repository, commit, inputs, entries);
    }
}
