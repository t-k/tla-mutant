package dev.tlamutant.experiment;

import dev.tlamutant.security.BoundedFileSnapshot;
import java.util.Objects;

public record ExperimentInputIdentity(
        Artifact module, Artifact config, Artifact manifest, Artifact tools, Artifact application) {
    public ExperimentInputIdentity {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(application, "application");
    }

    public record Artifact(String sha256, long sizeBytes) {
        public Artifact {
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}") || sizeBytes <= 0) {
                throw new IllegalArgumentException(
                        "Artifact identity must contain lowercase SHA-256 and positive size");
            }
        }

        public static Artifact from(BoundedFileSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            return new Artifact(snapshot.sha256(), snapshot.sizeBytes());
        }
    }
}
