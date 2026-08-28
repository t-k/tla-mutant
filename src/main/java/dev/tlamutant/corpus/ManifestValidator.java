package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.security.SafeRelativePath;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ManifestValidator {
    private ManifestValidator() {}

    public static CorpusManifest.Entry validateEntry(CorpusManifest.Entry entry, ExecutionPolicy.Limits globalLimits) {
        Objects.requireNonNull(entry, "entry");
        requireText(entry.id(), "id");
        requireText(entry.domain(), "domain");
        SafeRelativePath rootModule = SafeRelativePath.parse(entry.rootModule());
        SafeRelativePath config = SafeRelativePath.parse(entry.config());
        if (!rootModule.value().endsWith(".tla") || !config.value().endsWith(".cfg")) {
            throw new IllegalArgumentException("Root module and config extensions are invalid");
        }
        if (entry.files() == null || entry.files().isEmpty()) {
            throw new IllegalArgumentException("At least one source file is required");
        }
        Set<String> exact = new HashSet<>();
        Set<String> folded = new HashSet<>();
        for (CorpusManifest.SourceFile file : entry.files()) {
            Objects.requireNonNull(file, "source file");
            SafeRelativePath path = SafeRelativePath.parse(file.path());
            if (!exact.add(path.value())) {
                throw new IllegalArgumentException("Duplicate source path");
            }
            if (!folded.add(path.value().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Case-folding source-path collision");
            }
            if (file.sha256() == null || !file.sha256().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Source digest is invalid");
            }
            if (file.sizeBytes() <= 0) {
                throw new IllegalArgumentException("Source size must be positive");
            }
        }
        ExecutionPolicy.enforceMaximums(globalLimits, entry.limits());
        return entry;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
