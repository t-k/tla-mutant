package dev.tlamutant.experiment;

import java.util.Objects;

public record GapCandidate(String mutationId, String stateRelation, String witness) {
    public GapCandidate {
        Objects.requireNonNull(mutationId, "mutationId");
        Objects.requireNonNull(stateRelation, "stateRelation");
        Objects.requireNonNull(witness, "witness");
    }
}
