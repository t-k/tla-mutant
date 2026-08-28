package dev.tlamutant.mutation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record MutationCandidate(
        String id,
        MutationFamily family,
        String module,
        String definition,
        SourceRange range,
        String original,
        String replacement) {
    public MutationCandidate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(replacement, "replacement");
        if (original.isEmpty() || original.equals(replacement)) {
            throw new IllegalArgumentException("Mutation replacement must be non-empty and different");
        }
    }

    public static MutationCandidate create(
            MutationFamily family,
            String module,
            String definition,
            SourceRange range,
            String original,
            String replacement) {
        String identity = String.join(
                "\u0000",
                family.name(),
                module,
                definition,
                Integer.toString(range.start()),
                Integer.toString(range.endExclusive()),
                original,
                replacement);
        try {
            String digest = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 20);
            return new MutationCandidate(
                    family.name().toLowerCase() + "-" + digest,
                    family,
                    module,
                    definition,
                    range,
                    original,
                    replacement);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
