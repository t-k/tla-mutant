package dev.tlamutant.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.security.BoundedFileSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CorpusInputSnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mountsOnlyPrivateBytesThatWereVerifiedAgainstTheManifest() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("source"));
        byte[] model = "---- MODULE Spec ----\n====\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] config = "SPECIFICATION Spec\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source.resolve("Spec.tla"), model);
        Files.write(source.resolve("Spec.cfg"), config);
        CorpusManifest.Entry entry = entry(model, config);
        Path snapshotPath;
        Path snapshotRoot;

        try (CorpusInputSnapshot snapshot = CorpusInputSnapshot.create(source, entry)) {
            snapshotRoot = snapshot.root();
            snapshotPath = snapshot.root().resolve("Spec.tla");
            Files.writeString(source.resolve("Spec.tla"), "replaced after verification");

            assertEquals(new String(model, java.nio.charset.StandardCharsets.UTF_8), Files.readString(snapshotPath));
        }

        assertFalse(Files.exists(snapshotPath));
        assertFalse(Files.exists(snapshotRoot));
    }

    @Test
    void rejectsBytesThatDoNotMatchTheManifestIdentity() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("mismatch"));
        byte[] model = "---- MODULE Spec ----\n====\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] config = "SPECIFICATION Spec\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source.resolve("Spec.tla"), model);
        Files.write(source.resolve("Spec.cfg"), "different".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> CorpusInputSnapshot.create(source, entry(model, config)));
    }

    @Test
    void transportsTheExactManifestBytesIntoThePrivateSnapshot() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("full-property"));
        byte[] model = "---- MODULE Spec ----\n====\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] config = "SPECIFICATION Spec\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] manifest = "{\"schemaVersion\":1}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source.resolve("Spec.tla"), model);
        Files.write(source.resolve("Spec.cfg"), config);
        Path manifestPath = temporaryDirectory.resolve("manifest.json");
        Files.write(manifestPath, manifest);

        try (CorpusInputSnapshot snapshot = CorpusInputSnapshot.create(
                source, entry(model, config), BoundedFileSnapshot.read(manifestPath, 1024))) {
            assertEquals(
                    new String(manifest, java.nio.charset.StandardCharsets.UTF_8),
                    Files.readString(snapshot.root().resolve(CorpusInputSnapshot.FULL_PROPERTY_MANIFEST)));
        }
    }

    private static CorpusManifest.Entry entry(byte[] model, byte[] config) throws Exception {
        return new CorpusManifest.Entry(
                "spec",
                "domain",
                "Spec.tla",
                "Spec.cfg",
                List.of(file("Spec.tla", model), file("Spec.cfg", config)),
                List.of("TypeOK"),
                List.of(),
                new ExecutionPolicy.Limits(1, 1, 1, 1, 1000, 2, 1, 1, 1, 1),
                null);
    }

    private static CorpusManifest.SourceFile file(String path, byte[] bytes) throws Exception {
        String digest =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new CorpusManifest.SourceFile(path, path, digest, bytes.length);
    }
}
