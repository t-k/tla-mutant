package dev.tlamutant.census;

import java.util.Objects;

public record GcdCensusSite(
        String candidateId,
        String module,
        String definition,
        String guard,
        int guardLevel,
        int remainingStateConjuncts,
        int actionSuccessorConjuncts,
        boolean conforms) {
    public GcdCensusSite {
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(guard, "guard");
        if (!candidateId.matches("gcd-[0-9a-f]{20}")
                || guard.length() > 500
                || guardLevel < 0
                || remainingStateConjuncts < 0
                || actionSuccessorConjuncts < 0
                || conforms != (guardLevel <= 1 && actionSuccessorConjuncts > 0)) {
            throw new IllegalArgumentException("Invalid GCD census site");
        }
    }
}
