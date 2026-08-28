package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.security.Digests;
import dev.tlamutant.security.SafeRelativePath;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

public final class CorpusFetcher {
    private static final String REPOSITORY = "https://github.com/tlaplus/Examples";
    private static final String RAW_HOST = "raw.githubusercontent.com";
    private static final long MAX_SOURCE_BYTES = 8L * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public Path fetch(
            CorpusManifest manifest, CorpusManifest.Entry entry, ExecutionPolicy.Limits globalLimits, Path cacheRoot) {
        validateManifest(manifest);
        ManifestValidator.validateEntry(entry, globalLimits);
        SafeRelativePath commit = SafeRelativePath.parse(manifest.commit());
        SafeRelativePath id = SafeRelativePath.parse(entry.id());
        Path root = cacheRoot.toAbsolutePath().normalize();
        Path destination = root.resolve(commit.value()).resolve(id.value());
        if (Files.exists(destination)) {
            verifyCached(entry, destination);
            return destination;
        }

        Path staging = null;
        try {
            Files.createDirectories(destination.getParent());
            staging = Files.createTempDirectory(destination.getParent(), "." + id.value() + "-");
            for (CorpusManifest.SourceFile file : entry.files()) {
                SafeRelativePath local = SafeRelativePath.parse(file.path());
                Path target = staging.resolve(local.value()).normalize();
                if (!target.startsWith(staging)) {
                    throw new IllegalArgumentException("Corpus target escaped staging directory");
                }
                Files.createDirectories(target.getParent());
                download(manifest.commit(), file, target);
            }
            verifyCached(entry, staging);
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            staging = null;
            return destination;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Cannot fetch the pinned corpus entry " + entry.id(), exception);
        } finally {
            deleteTree(staging);
        }
    }

    private void download(String commit, CorpusManifest.SourceFile file, Path target)
            throws IOException, InterruptedException {
        if (file.sizeBytes() > MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Corpus source exceeds the per-file maximum");
        }
        rejectPrivateAddresses(RAW_HOST);
        URI uri = URI.create("https://" + RAW_HOST + "/tlaplus/Examples/" + commit + "/" + file.repositoryPath());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "tla-mutant-corpus-fetcher/1")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IllegalArgumentException("Pinned source download returned HTTP " + response.statusCode());
        }
        try (InputStream input = response.body()) {
            installVerified(input, target, file.sizeBytes(), file.sha256());
        }
    }

    static void installVerified(InputStream input, Path destination, long expectedSize, String expectedSha256)
            throws IOException {
        Objects.requireNonNull(input, "input");
        if (expectedSize <= 0 || expectedSize > MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Expected source size is outside the fixed bound");
        }
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected source digest is invalid");
        }
        Path temporary = Files.createTempFile(destination.getParent(), ".source-", ".part");
        try {
            MessageDigest digest = sha256();
            long total = 0;
            try (var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                while (true) {
                    int count = input.read(buffer);
                    if (count < 0) {
                        break;
                    }
                    total += count;
                    if (total > expectedSize) {
                        throw new IllegalArgumentException("Downloaded source exceeded its declared size");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (total != expectedSize || !actual.equals(expectedSha256)) {
                throw new IllegalArgumentException("Downloaded source does not match its manifest identity");
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void verifyCached(CorpusManifest.Entry entry, Path root) {
        try {
            long regularFiles;
            try (var paths = Files.walk(root)) {
                regularFiles = paths.filter(Files::isRegularFile).count();
            }
            if (regularFiles != entry.files().size()) {
                throw new IllegalArgumentException("Cached corpus entry contains an unexpected file set");
            }
            for (CorpusManifest.SourceFile file : entry.files()) {
                Path source = SafeRelativePath.parse(file.path()).resolveRegularFile(root);
                if (Files.size(source) != file.sizeBytes()
                        || !Digests.sha256(source, file.sizeBytes()).equals(file.sha256())) {
                    throw new IllegalArgumentException("Cached corpus source identity mismatch");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot verify cached corpus entry", exception);
        }
    }

    private static void validateManifest(CorpusManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        if (manifest.version() != 1
                || !REPOSITORY.equals(manifest.repository())
                || manifest.commit() == null
                || !manifest.commit().matches("[0-9a-f]{40}")
                || !"MIT".equals(manifest.license())) {
            throw new IllegalArgumentException("Corpus provenance is not an accepted pinned Examples revision");
        }
    }

    private static void rejectPrivateAddresses(String host) throws IOException {
        for (InetAddress address : InetAddress.getAllByName(host)) {
            byte[] bytes = address.getAddress();
            boolean uniqueLocalV6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            boolean carrierGradeNatV4 = bytes.length == 4
                    && Byte.toUnsignedInt(bytes[0]) == 100
                    && (Byte.toUnsignedInt(bytes[1]) & 0xc0) == 0x40;
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || uniqueLocalV6
                    || carrierGradeNatV4) {
                throw new IllegalArgumentException("Corpus source host resolved to a non-public address");
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Exact partial cache cleanup is verified by a later cache identity check.
                }
            });
        } catch (IOException ignored) {
            // Exact partial cache cleanup is verified by a later cache identity check.
        }
    }
}
