package dev.tlamutant.experiment;

import java.util.Objects;

public record CorpusExperimentInputIdentity(
        ExperimentInputIdentity.Artifact manifest,
        ExperimentInputIdentity.Artifact tools,
        ExperimentInputIdentity.Artifact application) {
    public CorpusExperimentInputIdentity {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(application, "application");
    }
}
