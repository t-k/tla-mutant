package dev.tlamutant.corpus;

import dev.tlamutant.security.SafeRelativePath;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class DerivedSourceEditValidator {
    private static final int MAX_EDITS = 1;
    private static final int MAX_INSERTED_BYTES = 4096;
    private static final Pattern OPERATOR_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");

    private DerivedSourceEditValidator() {}

    public static List<CorpusManifest.DerivedSourceEdit> validateList(List<CorpusManifest.DerivedSourceEdit> edits) {
        List<CorpusManifest.DerivedSourceEdit> requested =
                List.copyOf(Objects.requireNonNull(edits, "derivedSourceEdits"));
        if (requested.size() > MAX_EDITS) {
            throw new IllegalArgumentException("At most one derived source edit is allowed");
        }
        Set<String> paths = new HashSet<>();
        for (CorpusManifest.DerivedSourceEdit edit : requested) {
            validateMetadata(edit);
            if (!paths.add(edit.path())) {
                throw new IllegalArgumentException("Derived source edit paths must be unique");
            }
        }
        return requested;
    }

    public static void validateAgainstSource(CorpusManifest.DerivedSourceEdit edit, CorpusManifest.SourceFile source) {
        validateMetadata(edit);
        Objects.requireNonNull(source, "source");
        if (!source.path().equals(edit.path())
                || !source.sha256().equals(edit.baseSha256())
                || source.sizeBytes() != edit.baseSizeBytes()) {
            throw new IllegalArgumentException("Derived source edit base identity does not match the source file");
        }
    }

    private static void validateMetadata(CorpusManifest.DerivedSourceEdit edit) {
        Objects.requireNonNull(edit, "derived source edit");
        String path = SafeRelativePath.parse(edit.path()).value();
        if (!path.endsWith(".tla")) {
            throw new IllegalArgumentException("Derived source edits require a listed TLA+ path");
        }
        if (edit.definedOperator() == null
                || !OPERATOR_NAME.matcher(edit.definedOperator()).matches()) {
            throw new IllegalArgumentException("Derived source edit operator is invalid");
        }
        if (edit.baseSha256() == null
                || !edit.baseSha256().matches("[0-9a-f]{64}")
                || edit.baseSizeBytes() <= 0
                || edit.insertionOffset() < 0
                || edit.insertionOffset() > edit.baseSizeBytes()) {
            throw new IllegalArgumentException("Derived source edit base identity or offset is invalid");
        }
        if (edit.insertedText() == null || edit.insertedText().isEmpty()) {
            throw new IllegalArgumentException("Derived source insertion text is required");
        }
        byte[] inserted = edit.insertedText().getBytes(StandardCharsets.UTF_8);
        if (inserted.length > MAX_INSERTED_BYTES
                || edit.insertedText().codePoints().anyMatch(DerivedSourceEditValidator::isForbiddenCodePoint)) {
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

    private static boolean isForbiddenCodePoint(int codePoint) {
        return (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t')
                || codePoint == 0x061C
                || codePoint == 0x200E
                || codePoint == 0x200F
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }
}
