package dev.tlamutant.census;

import java.util.Objects;

public record CensusInputIdentity(Artifact manifest, Artifact policy, Artifact tools) {
    public CensusInputIdentity {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(tools, "tools");
    }

    public record Artifact(String sha256, long sizeBytes) {
        public Artifact {
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}") || sizeBytes <= 0) {
                throw new IllegalArgumentException("Census input artifact identity is invalid");
            }
        }
    }
}
