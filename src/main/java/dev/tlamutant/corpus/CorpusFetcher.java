package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.security.Digests;
import dev.tlamutant.security.SafeRelativePath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class CorpusFetcher {
    private static final String REPOSITORY = "https://github.com/tlaplus/Examples";
    private static final String RAW_HOST = "raw.githubusercontent.com";
    private static final long MAX_SOURCE_BYTES = 8L * 1024 * 1024;
    private static final java.nio.file.attribute.FileAttribute<java.util.Set<PosixFilePermission>> OWNER_DIRECTORY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    private static final java.nio.file.attribute.FileAttribute<java.util.Set<PosixFilePermission>> OWNER_FILE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

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
            staging = Files.createTempDirectory(destination.getParent(), "." + id.value() + "-", OWNER_DIRECTORY);
            for (CorpusManifest.SourceFile file : entry.files()) {
                SafeRelativePath local = SafeRelativePath.parse(file.path());
                Path target = staging.resolve(local.value()).normalize();
                if (!target.startsWith(staging)) {
                    throw new IllegalArgumentException("Corpus target escaped staging directory");
                }
                Files.createDirectories(target.getParent());
                Files.setPosixFilePermissions(target.getParent(), PosixFilePermissions.fromString("rwx------"));
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
        String requestPath = "/tlaplus/Examples/" + commit + "/" + file.repositoryPath();
        try (PinnedResponse response = openPinned(requestPath, file.sizeBytes());
                InputStream input = response.input()) {
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
        Path temporary = Files.createTempFile(destination.getParent(), ".source-", ".part", OWNER_FILE);
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

    private static PinnedResponse openPinned(String requestPath, long expectedSize) throws IOException {
        List<InetAddress> addresses = new ArrayList<>();
        for (InetAddress address : InetAddress.getAllByName(RAW_HOST)) {
            if (!isPublicGlobalAddress(address)) {
                throw new IllegalArgumentException("Corpus source host resolved to a non-public address");
            }
            addresses.add(address);
        }
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("Corpus source host has no verified public address");
        }
        IOException failure = null;
        for (InetAddress address : addresses) {
            try {
                return connectPinned(address, requestPath, expectedSize);
            } catch (IOException exception) {
                failure = exception;
            }
        }
        throw new IOException("Cannot connect to any verified corpus endpoint", failure);
    }

    private static PinnedResponse connectPinned(InetAddress address, String requestPath, long expectedSize)
            throws IOException {
        Socket plain = new Socket();
        try {
            plain.connect(new InetSocketAddress(address, 443), (int)
                    Duration.ofSeconds(20).toMillis());
            plain.setSoTimeout((int) Duration.ofSeconds(30).toMillis());
            SSLSocket tls = (SSLSocket)
                    ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, RAW_HOST, 443, true);
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            parameters.setServerNames(List.of(new SNIHostName(RAW_HOST)));
            tls.setSSLParameters(parameters);
            tls.startHandshake();
            OutputStream output = tls.getOutputStream();
            output.write(("GET "
                            + requestPath
                            + " HTTP/1.1\r\nHost: "
                            + RAW_HOST
                            + "\r\nUser-Agent: tla-mutant-corpus-fetcher/1\r\nAccept: text/plain\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.flush();

            InputStream input = tls.getInputStream();
            String status = readHttpLine(input, 4096);
            if (status == null || !status.matches("HTTP/1\\.[01] 200(?: .*)?")) {
                tls.close();
                throw new IllegalArgumentException("Pinned source download did not return HTTP 200");
            }
            Map<String, String> headers = new LinkedHashMap<>();
            int headerBytes = status.length();
            while (true) {
                String line = readHttpLine(input, 8192);
                if (line == null) {
                    tls.close();
                    throw new IllegalArgumentException("Pinned source response headers were incomplete");
                }
                headerBytes += line.length();
                if (headerBytes > 32 * 1024) {
                    tls.close();
                    throw new IllegalArgumentException("Pinned source response headers exceeded the bound");
                }
                if (line.isEmpty()) {
                    break;
                }
                int separator = line.indexOf(':');
                if (separator <= 0) {
                    tls.close();
                    throw new IllegalArgumentException("Pinned source response header was invalid");
                }
                String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                if (headers.putIfAbsent(name, value) != null) {
                    tls.close();
                    throw new IllegalArgumentException("Pinned source response repeated a security-relevant header");
                }
            }
            if (headers.containsKey("transfer-encoding")
                    || headers.containsKey("content-encoding")
                    || !Long.toString(expectedSize).equals(headers.get("content-length"))) {
                tls.close();
                throw new IllegalArgumentException("Pinned source response framing did not match the manifest");
            }
            return new PinnedResponse(tls, input);
        } catch (IOException | RuntimeException exception) {
            try {
                plain.close();
            } catch (IOException ignored) {
                exception.addSuppressed(ignored);
            }
            throw exception;
        }
    }

    private static String readHttpLine(InputStream input, int maximumBytes) throws IOException {
        byte[] bytes = new byte[maximumBytes];
        int length = 0;
        while (length < maximumBytes) {
            int value = input.read();
            if (value < 0) {
                return length == 0 ? null : new String(bytes, 0, length, StandardCharsets.US_ASCII);
            }
            if (value == '\n') {
                int contentLength = length > 0 && bytes[length - 1] == '\r' ? length - 1 : length;
                return new String(bytes, 0, contentLength, StandardCharsets.US_ASCII);
            }
            bytes[length++] = (byte) value;
        }
        throw new IllegalArgumentException("Pinned source response line exceeded the bound");
    }

    static boolean isPublicGlobalAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = Byte.toUnsignedInt(bytes[0]);
            int b = Byte.toUnsignedInt(bytes[1]);
            int c = Byte.toUnsignedInt(bytes[2]);
            return a != 0
                    && a != 10
                    && a != 127
                    && !(a == 100 && (b & 0xc0) == 0x40)
                    && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && b == 0 && c == 0)
                    && !(a == 192 && b == 0 && c == 2)
                    && !(a == 192 && b == 88 && c == 99)
                    && !(a == 192 && b == 168)
                    && !(a == 198 && (b == 18 || b == 19))
                    && !(a == 198 && b == 51 && c == 100)
                    && !(a == 203 && b == 0 && c == 113)
                    && a < 224;
        }
        if (bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            boolean globalUnicast = (first & 0xe0) == 0x20;
            boolean documentation = first == 0x20
                    && Byte.toUnsignedInt(bytes[1]) == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8;
            return globalUnicast && !documentation;
        }
        return false;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record PinnedResponse(SSLSocket socket, InputStream input) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            socket.close();
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
