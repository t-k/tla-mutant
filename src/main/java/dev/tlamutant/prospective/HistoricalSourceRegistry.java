package dev.tlamutant.prospective;

import dev.tlamutant.security.SafeRelativePath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record HistoricalSourceRegistry(
        int schemaVersion,
        String repository,
        String commit,
        int rootPathCount,
        String rootPathSetSha256,
        int sourceCount,
        String sourceSetSha256,
        List<SourceIdentity> sources) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    public HistoricalSourceRegistry {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Historical source registry schemaVersion must be 1");
        }
        repository = requireRepository(repository);
        if (commit == null || !COMMIT.matcher(commit).matches()) {
            throw new IllegalArgumentException("Historical source registry commit must be a lowercase Git SHA-1");
        }
        requireSha256(rootPathSetSha256, "rootPathSetSha256");
        requireSha256(sourceSetSha256, "sourceSetSha256");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Historical source registry must contain sources");
        }
        Set<String> compositeKeys = new HashSet<>();
        for (SourceIdentity source : sources) {
            Objects.requireNonNull(source, "source");
            String key = source.manifestPath()
                    + '\0'
                    + source.entryId()
                    + '\0'
                    + source.role()
                    + '\0'
                    + source.repositoryPath();
            if (!compositeKeys.add(key)) {
                throw new IllegalArgumentException("Historical source registry contains a duplicate source key");
            }
        }
        if (sourceCount != sources.size()) {
            throw new IllegalArgumentException("Historical source registry sourceCount does not match sources");
        }
        long computedRootPathCount = sources.stream()
                .filter(source -> source.role().equals("root"))
                .map(SourceIdentity::repositoryPath)
                .distinct()
                .count();
        if (rootPathCount != computedRootPathCount) {
            throw new IllegalArgumentException("Historical source registry rootPathCount does not match root sources");
        }
        String computedRootDigest = digest(canonicalRootBytes(sources));
        if (!rootPathSetSha256.equals(computedRootDigest)) {
            throw new IllegalArgumentException("Historical source registry rootPathSetSha256 does not match sources");
        }
        String computedSourceDigest = digest(canonicalSourceBytes(sources));
        if (!sourceSetSha256.equals(computedSourceDigest)) {
            throw new IllegalArgumentException("Historical source registry sourceSetSha256 does not match sources");
        }
    }

    public String canonicalSourceSetSha256() {
        return digest(canonicalSourceBytes(sources));
    }

    public String canonicalRootPathSetSha256() {
        return digest(canonicalRootBytes(sources));
    }

    private static String requireRepository(String value) {
        if (value == null || value.length() > 500 || !value.startsWith("https://") || containsUnsafeText(value)) {
            throw new IllegalArgumentException("Historical repository must be a bounded safe HTTPS URL");
        }
        return value;
    }

    private static boolean containsUnsafeText(String value) {
        return value.codePoints()
                .anyMatch(codePoint -> Character.isISOControl(codePoint)
                        || codePoint == 0x202A
                        || codePoint == 0x202B
                        || codePoint == 0x202C
                        || codePoint == 0x202D
                        || codePoint == 0x202E
                        || codePoint == 0x2066
                        || codePoint == 0x2067
                        || codePoint == 0x2068
                        || codePoint == 0x2069);
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
    }

    private static byte[] canonicalSourceBytes(List<SourceIdentity> sources) {
        StringBuilder canonical = new StringBuilder();
        sources.stream().sorted(SourceIdentity.CANONICAL_ORDER).forEach(source -> canonical
                .append(source.repositoryPath())
                .append('\t')
                .append(source.sha256())
                .append('\t')
                .append(source.sizeBytes())
                .append('\t')
                .append(source.manifestPath())
                .append('\t')
                .append(source.entryId())
                .append('\t')
                .append(source.role())
                .append('\n'));
        return canonical.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] canonicalRootBytes(List<SourceIdentity> sources) {
        StringBuilder canonical = new StringBuilder();
        sources.stream()
                .filter(source -> source.role().equals("root"))
                .map(SourceIdentity::repositoryPath)
                .distinct()
                .sorted()
                .forEach(path -> canonical.append(path).append('\n'));
        return canonical.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record SourceIdentity(
            String manifestPath, String entryId, String role, String repositoryPath, String sha256, long sizeBytes) {
        public static final Comparator<SourceIdentity> CANONICAL_ORDER = Comparator.comparing(
                        SourceIdentity::repositoryPath)
                .thenComparing(SourceIdentity::sha256)
                .thenComparingLong(SourceIdentity::sizeBytes)
                .thenComparing(SourceIdentity::manifestPath)
                .thenComparing(SourceIdentity::entryId)
                .thenComparing(SourceIdentity::role);
        private static final Set<String> ROLES = Set.of("root", "config", "dependency");
        private static final Pattern ENTRY_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

        public SourceIdentity {
            SafeRelativePath.parse(manifestPath);
            if (entryId == null || !ENTRY_ID.matcher(entryId).matches()) {
                throw new IllegalArgumentException("Historical source entryId is not portable");
            }
            if (!ROLES.contains(role)) {
                throw new IllegalArgumentException("Historical source role is unknown");
            }
            SafeRelativePath.parse(repositoryPath);
            requireSha256(sha256, "source sha256");
            if (sizeBytes <= 0) {
                throw new IllegalArgumentException("Historical source sizeBytes must be positive");
            }
        }
    }
}
