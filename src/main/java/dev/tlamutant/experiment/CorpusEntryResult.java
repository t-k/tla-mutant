package dev.tlamutant.experiment;

import java.util.Objects;

public record CorpusEntryResult(String id, String domain, String status, String diagnostic, ExperimentReport report) {
    public CorpusEntryResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(status, "status");
        if (status.equals("COMPLETED") != (report != null)) {
            throw new IllegalArgumentException("Only a completed corpus entry has an experiment report");
        }
        if (status.equals("COMPLETED") && diagnostic != null) {
            throw new IllegalArgumentException("A completed corpus entry has no failure diagnostic");
        }
    }
}
