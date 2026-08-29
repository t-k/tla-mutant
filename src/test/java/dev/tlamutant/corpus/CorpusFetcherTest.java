package dev.tlamutant.corpus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.config.ExecutionPolicy;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CorpusFetcherTest {
    @Test
    void verifiedInstallerStopsAfterTheDeclaredLengthWithoutWaitingForEof() throws Exception {
        byte[] content = "exact".getBytes(StandardCharsets.UTF_8);
        InputStream noEof = new ByteArrayInputStream(content) {
            @Override
            public int read(byte[] bytes, int offset, int length) {
                if (available() == 0) {
                    throw new AssertionError("installer read beyond the declared content length");
                }
                return super.read(bytes, offset, length);
            }
        };
        Path installed = temporaryDirectory.resolve("exact.tla");

        CorpusFetcher.installVerified(noEof, installed, content.length, sha256(content));

        assertArrayEquals(content, Files.readAllBytes(installed));
    }

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

    @Test
    void acceptsOnlyPublicGlobalEndpointAddresses() throws Exception {
        for (String rejected : new String[] {
            "0.0.0.1",
            "10.0.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "192.0.2.1",
            "192.168.1.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "::1",
            "fc00::1",
            "fe80::1",
            "2001:db8::1"
        }) {
            assertFalse(CorpusFetcher.isPublicGlobalAddress(java.net.InetAddress.getByName(rejected)), rejected);
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                CorpusFetcher.isPublicGlobalAddress(java.net.InetAddress.getByName("8.8.8.8")));
        org.junit.jupiter.api.Assertions.assertTrue(
                CorpusFetcher.isPublicGlobalAddress(java.net.InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void acceptsTheVersionThreeV9ProvenanceFromAPrepopulatedVerifiedCacheWithoutNetwork() throws Exception {
        byte[] model = "m".getBytes(StandardCharsets.UTF_8);
        byte[] config = "c".getBytes(StandardCharsets.UTF_8);
        CorpusManifest.Entry entry = new CorpusManifest.Entry(
                "sample",
                "domain",
                "Spec.tla",
                "Spec.cfg",
                List.of(
                        new CorpusManifest.SourceFile("Spec.tla", "Spec.tla", sha256(model), model.length),
                        new CorpusManifest.SourceFile("Spec.cfg", "Spec.cfg", sha256(config), config.length)),
                List.of("TypeOK"),
                List.of(),
                List.of("NoOverlap"),
                List.of(new CorpusManifest.DerivedSourceEdit(
                        "Spec.tla", "NoOverlap", sha256(model), 1, 1, "x", "2".repeat(64), 2)),
                new ExecutionPolicy.Limits(1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                null);
        CorpusManifest v9 = new CorpusManifest(
                3, "gcd-stratified-v9", "https://github.com/tlaplus/Examples", "a".repeat(40), "MIT", List.of(entry));
        CorpusManifest wrongVersion =
                new CorpusManifest(3, "gcd-stratified-v8", v9.repository(), v9.commit(), v9.license(), v9.entries());
        Path cached = temporaryDirectory.resolve(v9.commit()).resolve(entry.id());
        Files.createDirectories(cached);
        Files.write(cached.resolve("Spec.tla"), model);
        Files.write(cached.resolve("Spec.cfg"), config);

        Path fetched =
                assertDoesNotThrow(() -> new CorpusFetcher().fetch(v9, entry, entry.limits(), temporaryDirectory));
        assertArrayEquals(model, Files.readAllBytes(fetched.resolve("Spec.tla")));
        assertThrows(IllegalArgumentException.class, () -> CorpusFetcher.validateManifest(wrongVersion));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
