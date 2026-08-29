package dev.tlamutant.census;

import dev.tlamutant.corpus.CorpusManifest;
import java.util.Objects;

public record CorpusCensusEntryResult(
        String id,
        String domain,
        CorpusManifest.Selection selection,
        String status,
        String diagnostic,
        GcdCensusReport report) {
    public CorpusCensusEntryResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(status, "status");
        if (status.equals("COMPLETED") != (report != null)) {
            throw new IllegalArgumentException("Only a completed corpus census entry has a report");
        }
        if (status.equals("COMPLETED") && diagnostic != null) {
            throw new IllegalArgumentException("A completed corpus census entry has no failure diagnostic");
        }
        if (diagnostic != null && diagnostic.length() > 2000) {
            throw new IllegalArgumentException("Corpus census diagnostic exceeds the fixed bound");
        }
    }
}
