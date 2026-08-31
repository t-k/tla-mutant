package dev.tlamutant.experiment;

import dev.tlamutant.security.BoundedFileSnapshot;
import dev.tlamutant.security.SafeRelativePath;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ExperimentInputIdentity(
        Artifact module,
        Artifact config,
        Artifact manifest,
        Artifact tools,
        Artifact application,
        List<ModelFile> modelFiles) {
    public ExperimentInputIdentity {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(application, "application");
        modelFiles = List.copyOf(Objects.requireNonNull(modelFiles, "modelFiles"));
        Set<String> paths = new HashSet<>();
        if (modelFiles.stream().anyMatch(file -> !paths.add(file.path()))
                || !modelFiles.equals(modelFiles.stream()
                        .sorted(java.util.Comparator.comparing(ModelFile::path))
                        .toList())) {
            throw new IllegalArgumentException("Model file identities must be unique and sorted by path");
        }
    }

    public ExperimentInputIdentity(
            Artifact module, Artifact config, Artifact manifest, Artifact tools, Artifact application) {
        this(module, config, manifest, tools, application, List.of());
    }

    public ExperimentInputIdentity withModelFiles(List<ModelFile> files) {
        return new ExperimentInputIdentity(module, config, manifest, tools, application, files);
    }

    public record ModelFile(String path, Artifact identity) {
        public ModelFile {
            path = SafeRelativePath.parse(path).value().toString().replace(java.io.File.separatorChar, '/');
            Objects.requireNonNull(identity, "identity");
        }
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
