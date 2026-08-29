package dev.tlamutant.mutation;

import java.util.Objects;

public record GcdSite(
        MutationCandidate candidate,
        int guardLevel,
        int remainingStateConjuncts,
        int actionSuccessorConjuncts,
        boolean conforms) {
    public GcdSite {
        Objects.requireNonNull(candidate, "candidate");
        if (guardLevel < 0 || remainingStateConjuncts < 0 || actionSuccessorConjuncts < 0) {
            throw new IllegalArgumentException("GCD structural counts must be non-negative");
        }
    }

    public boolean hasActionSuccessor() {
        return actionSuccessorConjuncts > 0;
    }
}
