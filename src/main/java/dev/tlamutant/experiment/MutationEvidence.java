package dev.tlamutant.experiment;

import dev.tlamutant.mutation.MutationCandidate;
import dev.tlamutant.mutation.SourceDocument;
import dev.tlamutant.tlc.TlcExecution;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record MutationEvidence(
        String module,
        String definition,
        SourceRange range,
        String sourceSha256,
        String mutatedSourceSha256,
        Patch patch,
        String violatedProperty,
        int tlcExitCode,
        List<Integer> toolEventCodes) {
    public MutationEvidence {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(range, "range");
        requireSha256(sourceSha256, "sourceSha256");
        requireSha256(mutatedSourceSha256, "mutatedSourceSha256");
        Objects.requireNonNull(patch, "patch");
        if (violatedProperty != null && !violatedProperty.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new IllegalArgumentException("Violated property name is unsafe");
        }
        toolEventCodes = List.copyOf(Objects.requireNonNull(toolEventCodes, "toolEventCodes"));
        if (toolEventCodes.stream().anyMatch(code -> code == null || code <= 0)
                || !toolEventCodes.equals(
                        toolEventCodes.stream().distinct().sorted().toList())) {
            throw new IllegalArgumentException("Tool event codes must be positive, unique, and sorted");
        }
    }

    public static MutationEvidence create(
            MutationCandidate candidate, SourceDocument source, SourceDocument patched, TlcExecution execution) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(patched, "patched");
        Objects.requireNonNull(execution, "execution");
        String sourceDigest = sha256(source.content());
        String mutatedDigest = sha256(patched.content());
        SourceRange range =
                new SourceRange(candidate.range().start(), candidate.range().endExclusive());
        Patch patch = new Patch(
                candidate.module(),
                sourceDigest,
                mutatedDigest,
                range.startUtf16Offset(),
                range.endUtf16OffsetExclusive(),
                candidate.original(),
                candidate.replacement());
        if (!patch.apply(source.content()).equals(patched.content())) {
            throw new IllegalArgumentException("Patched source does not match the mutation candidate");
        }
        List<Integer> eventCodes = execution.events().stream()
                .filter(event -> event.severity() > 0)
                .map(event -> event.code())
                .distinct()
                .sorted()
                .toList();
        return new MutationEvidence(
                candidate.module(),
                candidate.definition(),
                range,
                sourceDigest,
                mutatedDigest,
                patch,
                execution.violatedProperty(),
                execution.exitCode(),
                eventCodes);
    }

    public record SourceRange(int startUtf16Offset, int endUtf16OffsetExclusive) {
        public SourceRange {
            if (startUtf16Offset < 0 || endUtf16OffsetExclusive <= startUtf16Offset) {
                throw new IllegalArgumentException("Source range must be non-empty and ordered");
            }
        }
    }

    public record Patch(
            String module,
            String sourceSha256,
            String mutatedSourceSha256,
            int startUtf16Offset,
            int endUtf16OffsetExclusive,
            String expected,
            String replacement) {
        public Patch {
            Objects.requireNonNull(module, "module");
            requireSha256(sourceSha256, "sourceSha256");
            requireSha256(mutatedSourceSha256, "mutatedSourceSha256");
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(replacement, "replacement");
            if (startUtf16Offset < 0
                    || endUtf16OffsetExclusive <= startUtf16Offset
                    || expected.length() != endUtf16OffsetExclusive - startUtf16Offset
                    || expected.isEmpty()
                    || expected.equals(replacement)) {
                throw new IllegalArgumentException("Mutation patch range and replacement are invalid");
            }
        }

        public String apply(String source) {
            requireDigest(source, sourceSha256, "source");
            if (endUtf16OffsetExclusive > source.length()
                    || !source.substring(startUtf16Offset, endUtf16OffsetExclusive)
                            .equals(expected)) {
                throw new IllegalArgumentException("Mutation patch expected source slice does not match");
            }
            String mutated =
                    source.substring(0, startUtf16Offset) + replacement + source.substring(endUtf16OffsetExclusive);
            requireDigest(mutated, mutatedSourceSha256, "mutated source");
            return mutated;
        }

        public String reverse(String mutated) {
            requireDigest(mutated, mutatedSourceSha256, "mutated source");
            int replacementEnd = startUtf16Offset + replacement.length();
            if (replacementEnd > mutated.length()
                    || !mutated.substring(startUtf16Offset, replacementEnd).equals(replacement)) {
                throw new IllegalArgumentException("Mutation patch replacement slice does not match");
            }
            String source = mutated.substring(0, startUtf16Offset) + expected + mutated.substring(replacementEnd);
            requireDigest(source, sourceSha256, "source");
            return source;
        }
    }

    private static void requireDigest(String value, String expected, String label) {
        if (!sha256(value).equals(expected)) {
            throw new IllegalArgumentException(label + " digest does not match the mutation patch");
        }
    }

    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
