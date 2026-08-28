package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.security.SafeRelativePath;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ManifestValidator {
    private static final int MAX_FILES_PER_ENTRY = 64;
    private static final long MAX_TOTAL_SOURCE_BYTES = 32L * 1024 * 1024;

    private ManifestValidator() {}

    public static CorpusManifest.Entry validateEntry(CorpusManifest.Entry entry, ExecutionPolicy.Limits globalLimits) {
        Objects.requireNonNull(entry, "entry");
        requireText(entry.id(), "id");
        SafeRelativePath id = SafeRelativePath.parse(entry.id());
        if (id.value().contains("/")) {
            throw new IllegalArgumentException("id must be one portable path segment");
        }
        requireText(entry.domain(), "domain");
        SafeRelativePath rootModule = SafeRelativePath.parse(entry.rootModule());
        SafeRelativePath config = SafeRelativePath.parse(entry.config());
        if (!rootModule.value().endsWith(".tla") || !config.value().endsWith(".cfg")) {
            throw new IllegalArgumentException("Root module and config extensions are invalid");
        }
        if (entry.files() == null || entry.files().isEmpty()) {
            throw new IllegalArgumentException("At least one source file is required");
        }
        if (entry.files().size() > MAX_FILES_PER_ENTRY) {
            throw new IllegalArgumentException("Corpus entry contains too many source files");
        }
        Set<String> exact = new HashSet<>();
        Set<String> folded = new HashSet<>();
        Set<String> repositoryPaths = new HashSet<>();
        long totalSourceBytes = 0;
        for (CorpusManifest.SourceFile file : entry.files()) {
            Objects.requireNonNull(file, "source file");
            SafeRelativePath path = SafeRelativePath.parse(file.path());
            SafeRelativePath repositoryPath = SafeRelativePath.parse(file.repositoryPath());
            if (!exact.add(path.value())) {
                throw new IllegalArgumentException("Duplicate source path");
            }
            if (!folded.add(path.value().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Case-folding source-path collision");
            }
            if (!repositoryPaths.add(repositoryPath.value())) {
                throw new IllegalArgumentException("Duplicate repository source path");
            }
            if (file.sha256() == null || !file.sha256().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Source digest is invalid");
            }
            if (file.sizeBytes() <= 0) {
                throw new IllegalArgumentException("Source size must be positive");
            }
            try {
                totalSourceBytes = Math.addExact(totalSourceBytes, file.sizeBytes());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Corpus source size total overflowed", exception);
            }
        }
        if (totalSourceBytes > MAX_TOTAL_SOURCE_BYTES) {
            throw new IllegalArgumentException("Corpus entry source total exceeds the fixed bound");
        }
        if (!exact.contains(rootModule.value()) || !exact.contains(config.value())) {
            throw new IllegalArgumentException("Root module and config must be listed source files");
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
