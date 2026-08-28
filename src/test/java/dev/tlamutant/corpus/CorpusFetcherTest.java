package dev.tlamutant.corpus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CorpusFetcherTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void installsOnlyAnExactSizeAndDigestMatch() throws Exception {
        byte[] content = "---- MODULE Sample ----\n====\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path destination = temporaryDirectory.resolve("Sample.tla");

        CorpusFetcher.installVerified(new ByteArrayInputStream(content), destination, content.length, sha256(content));

        assertArrayEquals(content, Files.readAllBytes(destination));
    }

    @Test
    void removesPartialOutputAfterSizeOrDigestMismatch() {
        byte[] content = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path wrongSize = temporaryDirectory.resolve("wrong-size.tla");
        Path wrongDigest = temporaryDirectory.resolve("wrong-digest.tla");

        assertThrows(
                IllegalArgumentException.class,
                () -> CorpusFetcher.installVerified(
                        new ByteArrayInputStream(content), wrongSize, content.length - 1, sha256(content)));
        assertThrows(
                IllegalArgumentException.class,
                () -> CorpusFetcher.installVerified(
                        new ByteArrayInputStream(content), wrongDigest, content.length, "0".repeat(64)));
        assertFalse(Files.exists(wrongSize));
        assertFalse(Files.exists(wrongDigest));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
