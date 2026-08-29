package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.security.SafeRelativePath;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class ManifestValidator {
    private static final int MAX_FILES_PER_ENTRY = 64;
    private static final long MAX_TOTAL_SOURCE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_INVARIANTS_PER_ENTRY = 64;
    private static final int MAX_DERIVED_SOURCE_EDITS = 1;
    private static final int MAX_INSERTED_SOURCE_BYTES = 4096;
    private static final Pattern OPERATOR_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
    private static final Pattern ENTRY_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private ManifestValidator() {}

    public static CorpusManifest.Entry validateEntry(CorpusManifest.Entry entry, ExecutionPolicy.Limits globalLimits) {
        Objects.requireNonNull(entry, "entry");
        requireText(entry.id(), "id");
        SafeRelativePath id = SafeRelativePath.parse(entry.id());
        if (id.value().contains("/") || !ENTRY_ID.matcher(id.value()).matches()) {
            throw new IllegalArgumentException("id must be one safe bounded identifier");
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
        validateInvariants(entry.typeInvariants(), entry.targetInvariants(), entry.adoptedTargetInvariants());
        validateDerivedSourceEdits(entry.files(), entry.derivedSourceEdits());
        ExecutionPolicy.enforceMaximums(globalLimits, entry.limits());
        return entry;
    }

    private static void validateInvariants(
            List<String> typeInvariants, List<String> targetInvariants, List<String> adoptedTargetInvariants) {
        if (typeInvariants == null || targetInvariants == null || adoptedTargetInvariants == null) {
            throw new IllegalArgumentException("Invariant lists are required");
        }
        int count = Math.addExact(
                Math.addExact(typeInvariants.size(), targetInvariants.size()), adoptedTargetInvariants.size());
        if (count == 0 || count > MAX_INVARIANTS_PER_ENTRY) {
            throw new IllegalArgumentException("Each entry must declare 1 to 64 state invariants");
        }
        Set<String> names = new HashSet<>();
        for (String name : java.util.stream.Stream.of(typeInvariants, targetInvariants, adoptedTargetInvariants)
                .flatMap(List::stream)
                .toList()) {
            if (name == null || !OPERATOR_NAME.matcher(name).matches() || !names.add(name)) {
                throw new IllegalArgumentException("State invariant names must be safe and unique");
            }
        }
    }

    private static void validateDerivedSourceEdits(
            List<CorpusManifest.SourceFile> files, List<CorpusManifest.DerivedSourceEdit> edits) {
        if (edits == null || edits.size() > MAX_DERIVED_SOURCE_EDITS) {
            throw new IllegalArgumentException("At most one derived source edit is allowed per entry");
        }
        Set<String> paths = new HashSet<>();
        for (CorpusManifest.DerivedSourceEdit edit : edits) {
            Objects.requireNonNull(edit, "derived source edit");
            SafeRelativePath path = SafeRelativePath.parse(edit.path());
            if (!path.value().endsWith(".tla") || !paths.add(path.value())) {
                throw new IllegalArgumentException("Derived source edits require a unique listed TLA+ path");
            }
            CorpusManifest.SourceFile source = files.stream()
                    .filter(file -> file.path().equals(path.value()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Derived source edit path is not listed"));
            if (!source.sha256().equals(edit.baseSha256()) || source.sizeBytes() != edit.baseSizeBytes()) {
                throw new IllegalArgumentException("Derived source edit base identity does not match the source file");
            }
            if (edit.insertionOffset() < 0 || edit.insertionOffset() > edit.baseSizeBytes()) {
                throw new IllegalArgumentException("Derived source insertion offset is outside the base file");
            }
            if (edit.insertedText() == null || edit.insertedText().isEmpty()) {
                throw new IllegalArgumentException("Derived source insertion text is required");
            }
            byte[] inserted = edit.insertedText().getBytes(StandardCharsets.UTF_8);
            if (inserted.length > MAX_INSERTED_SOURCE_BYTES
                    || edit.insertedText().codePoints().anyMatch(ManifestValidator::isForbiddenInsertedCodePoint)) {
                throw new IllegalArgumentException("Derived source insertion text is unsafe or too large");
            }
            if (edit.derivedSha256() == null || !edit.derivedSha256().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Derived source digest is invalid");
            }
            long expectedDerivedSize;
            try {
                expectedDerivedSize = Math.addExact(edit.baseSizeBytes(), inserted.length);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Derived source size overflowed", exception);
            }
            if (edit.derivedSizeBytes() != expectedDerivedSize) {
                throw new IllegalArgumentException("Derived source size does not match the insertion");
            }
        }
    }

    private static boolean isForbiddenInsertedCodePoint(int codePoint) {
        return (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t')
                || codePoint == 0x061C
                || codePoint == 0x200E
                || codePoint == 0x200F
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
