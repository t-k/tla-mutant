package dev.tlamutant.mutation;

import java.util.Objects;

public final class SourcePatcher {
    public SourceDocument apply(SourceDocument document, MutationCandidate candidate) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(candidate, "candidate");
        document.validate(candidate.range());
        String observed = document.slice(candidate.range());
        if (!observed.equals(candidate.original())) {
            throw new IllegalArgumentException("Mutation source does not match the analyzed range");
        }
        String patched = document.content().substring(0, candidate.range().start())
                + candidate.replacement()
                + document.content().substring(candidate.range().endExclusive());
        if (patched.equals(document.content())) {
            throw new IllegalArgumentException("Mutation produced an empty diff");
        }
        return SourceDocument.fromString(patched);
    }
}
