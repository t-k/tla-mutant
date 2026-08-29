package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

final class HistoricalSourceRegistryTest {
    @Test
    void computesCanonicalSourceAndRootPathIdentities() throws Exception {
        List<HistoricalSourceRegistry.SourceIdentity> sources = List.of(
                source("corpus/v9.json", "entry-b", "config", "specifications/B/B.cfg", "b", 22),
                source("corpus/v8.json", "entry-a", "root", "specifications/A/A.tla", "a", 11),
                source("corpus/v8.json", "entry-a", "dependency", "specifications/A/Z.tla", "c", 33));
        String canonicalSources = "specifications/A/A.tla\t" + "a".repeat(64)
                + "\t11\tcorpus/v8.json\tentry-a\troot\n"
                + "specifications/A/Z.tla\t"
                + "c".repeat(64)
                + "\t33\tcorpus/v8.json\tentry-a\tdependency\n"
                + "specifications/B/B.cfg\t"
                + "b".repeat(64)
                + "\t22\tcorpus/v9.json\tentry-b\tconfig\n";
        String canonicalRoots = "specifications/A/A.tla\n";

        HistoricalSourceRegistry registry = new HistoricalSourceRegistry(
                1,
                "https://github.com/tlaplus/Examples.git",
                "d".repeat(40),
                1,
                sha256(canonicalRoots),
                3,
                sha256(canonicalSources),
                sources);

        assertEquals(sha256(canonicalSources), registry.canonicalSourceSetSha256());
        assertEquals(sha256(canonicalRoots), registry.canonicalRootPathSetSha256());
        assertEquals(List.copyOf(sources), registry.sources());
        assertThrows(
                UnsupportedOperationException.class, () -> registry.sources().add(sources.getFirst()));
    }

    @Test
    void rejectsDeclaredCountOrDigestMismatch() throws Exception {
        List<HistoricalSourceRegistry.SourceIdentity> sources =
                List.of(source("corpus/v8.json", "entry-a", "root", "specifications/A/A.tla", "a", 11));
        String rootDigest = sha256("specifications/A/A.tla\n");
        String sourceDigest =
                sha256("specifications/A/A.tla\t" + "a".repeat(64) + "\t11\tcorpus/v8.json\tentry-a\troot\n");

        assertThrows(IllegalArgumentException.class, () -> registry(2, rootDigest, 1, sourceDigest, sources));
        assertThrows(IllegalArgumentException.class, () -> registry(1, "0".repeat(64), 1, sourceDigest, sources));
        assertThrows(IllegalArgumentException.class, () -> registry(1, rootDigest, 2, sourceDigest, sources));
        assertThrows(IllegalArgumentException.class, () -> registry(1, rootDigest, 1, "0".repeat(64), sources));
    }

    @Test
    void rejectsDuplicateCompositeKeysAndUnsafeFields() throws Exception {
        HistoricalSourceRegistry.SourceIdentity valid =
                source("corpus/v8.json", "entry-a", "root", "specifications/A/A.tla", "a", 11);
        List<HistoricalSourceRegistry.SourceIdentity> duplicates = List.of(valid, valid);

        assertThrows(IllegalArgumentException.class, () -> uncheckedRegistry(duplicates));
        assertThrows(
                IllegalArgumentException.class,
                () -> source("../v8.json", "entry-a", "root", "specifications/A/A.tla", "a", 11));
        assertThrows(
                IllegalArgumentException.class,
                () -> source("corpus/v8.json", "entry-a", "unknown", "specifications/A/A.tla", "a", 11));
        assertThrows(
                IllegalArgumentException.class,
                () -> source("corpus/v8.json", "entry-a", "root", "specifications/A/A.tla", "a", 0));
    }

    @Test
    void copiesTheCallerOwnedSourceList() throws Exception {
        List<HistoricalSourceRegistry.SourceIdentity> mutable = new ArrayList<>();
        mutable.add(source("corpus/v8.json", "entry-a", "root", "specifications/A/A.tla", "a", 11));
        HistoricalSourceRegistry registry = uncheckedRegistry(mutable);

        mutable.clear();

        assertEquals(1, registry.sources().size());
        assertTrue(registry.sources().getFirst().repositoryPath().endsWith("A.tla"));
    }

    private static HistoricalSourceRegistry registry(
            int rootCount,
            String rootDigest,
            int sourceCount,
            String sourceDigest,
            List<HistoricalSourceRegistry.SourceIdentity> sources) {
        return new HistoricalSourceRegistry(
                1,
                "https://github.com/tlaplus/Examples.git",
                "d".repeat(40),
                rootCount,
                rootDigest,
                sourceCount,
                sourceDigest,
                sources);
    }

    private static HistoricalSourceRegistry uncheckedRegistry(List<HistoricalSourceRegistry.SourceIdentity> sources)
            throws Exception {
        String canonicalSources = sources.stream()
                .sorted(HistoricalSourceRegistry.SourceIdentity.CANONICAL_ORDER)
                .map(source -> source.repositoryPath() + "\t" + source.sha256() + "\t" + source.sizeBytes() + "\t"
                        + source.manifestPath() + "\t" + source.entryId() + "\t" + source.role() + "\n")
                .reduce("", String::concat);
        String canonicalRoots = sources.stream()
                .filter(source -> source.role().equals("root"))
                .map(HistoricalSourceRegistry.SourceIdentity::repositoryPath)
                .distinct()
                .sorted()
                .map(path -> path + "\n")
                .reduce("", String::concat);
        long rootCount = sources.stream()
                .filter(source -> source.role().equals("root"))
                .map(HistoricalSourceRegistry.SourceIdentity::repositoryPath)
                .distinct()
                .count();
        return registry((int) rootCount, sha256(canonicalRoots), sources.size(), sha256(canonicalSources), sources);
    }

    private static HistoricalSourceRegistry.SourceIdentity source(
            String manifestPath,
            String entryId,
            String role,
            String repositoryPath,
            String digestCharacter,
            long size) {
        return new HistoricalSourceRegistry.SourceIdentity(
                manifestPath, entryId, role, repositoryPath, digestCharacter.repeat(64), size);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
