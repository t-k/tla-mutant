package dev.tlamutant.mutation;

public record SourceRange(int start, int endExclusive) {
    public SourceRange {
        if (start < 0 || endExclusive <= start) {
            throw new IllegalArgumentException("Source range must be non-empty and ordered");
        }
    }
}
