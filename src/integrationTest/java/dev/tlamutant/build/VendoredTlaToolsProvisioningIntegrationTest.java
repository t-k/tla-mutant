package dev.tlamutant.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VendoredTlaToolsProvisioningIntegrationTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);
    private static final String VENDOR_PATH = "vendor/tla-tools/1.8.0-9787e65/tla2tools.jar";
    private static final String CACHED_PATH = ".tools/tla2tools-1.8.0.jar";
    private static final String SHA256 = "eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a";

    @TempDir
    Path temporaryDirectory;

    @Test
    void provisionsReviewedArtifactOfflineWithOwnerOnlyPermissions() throws Exception {
        Path checkout = createCheckout("offline-provision");

        GradleResult result = runGradle(checkout, "verifyTlaTools");

        assertEquals(0, result.exitCode(), result.output());
        Path cachedArtifact = checkout.resolve(CACHED_PATH);
        assertEquals(SHA256, sha256(cachedArtifact));
        assertEquals(
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(cachedArtifact));
    }

    @Test
    void fetchCompatibilityAliasProvisionsReviewedArtifactOffline() throws Exception {
        Path checkout = createCheckout("fetch-alias");

        GradleResult result = runGradle(checkout, "fetchTlaTools");

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(SHA256, sha256(checkout.resolve(CACHED_PATH)));
    }

    @Test
    void rejectsTruncatedSourceAndPreservesExistingCache() throws Exception {
        Path checkout = createCheckout("truncated-source");
        Path cachedArtifact = checkout.resolve(CACHED_PATH);
        Files.createDirectories(cachedArtifact.getParent());
        Files.copy(checkout.resolve(VENDOR_PATH), cachedArtifact);
        Path source = checkout.resolve(VENDOR_PATH);
        byte[] truncated = Files.readAllBytes(source);
        Files.write(source, java.util.Arrays.copyOf(truncated, truncated.length - 1));

        GradleResult result = runGradle(checkout, "provisionTlaTools");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Vendored TLA+ Tools size mismatch"), result.output());
        assertEquals(SHA256, sha256(cachedArtifact));
    }

    @Test
    void rejectsSameSizeSourceWithDifferentDigest() throws Exception {
        Path checkout = createCheckout("digest-mismatch");
        Path source = checkout.resolve(VENDOR_PATH);
        byte[] changed = Files.readAllBytes(source);
        changed[0] ^= 1;
        Files.write(source, changed);

        GradleResult result = runGradle(checkout, "provisionTlaTools");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Vendored TLA+ Tools SHA-256 mismatch"), result.output());
        assertFalse(Files.exists(checkout.resolve(CACHED_PATH)));
    }

    @Test
    void rejectsMissingSource() throws Exception {
        Path checkout = createCheckout("missing-source");
        Files.delete(checkout.resolve(VENDOR_PATH));

        GradleResult result = runGradle(checkout, "provisionTlaTools");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Vendored TLA+ Tools artifact must be a regular file"), result.output());
        assertFalse(Files.exists(checkout.resolve(CACHED_PATH)));
    }

    @Test
    void rejectsAbsoluteSourcePath() throws Exception {
        Path checkout = createCheckout("absolute-source");
        replaceVendorPath(checkout, checkout.resolve(VENDOR_PATH).toString());

        GradleResult result = runGradle(checkout, "provisionTlaTools");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Vendored TLA+ Tools path must be relative"), result.output());
        assertFalse(Files.exists(checkout.resolve(CACHED_PATH)));
    }

    @Test
    void rejectsSourcePathThatEscapesCheckout() throws Exception {
        Path checkout = createCheckout("escaping-source");
        replaceVendorPath(checkout, "../outside.jar");

        GradleResult result = runGradle(checkout, "provisionTlaTools");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Vendored TLA+ Tools path escapes the project"), result.output());
        assertFalse(Files.exists(checkout.resolve(CACHED_PATH)));
    }

    @Test
    void rejectsSourceSymlink() throws Exception {
        Path checkout = createCheckout("source-symlink");
        Path source = checkout.resolve(VENDOR_PATH);
        Path symlinkTarget = temporaryDirectory.resolve("source-symlink-target.jar");
        Files.move(source, symlinkTarget);
        Files.createSymbolicLink(source, symlinkTarget);

        GradleResult result = runGradle(checkout, "provisionTlaTools");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(
                result.output().contains("Vendored TLA+ Tools path must not contain symbolic links"), result.output());
        assertFalse(Files.exists(checkout.resolve(CACHED_PATH)));
    }

    @Test
    void rejectsDestinationDirectorySymlinkBeforeWritingOutsideCheckout() throws Exception {
        Path checkout = createCheckout("destination-symlink");
        Path escapedDirectory = temporaryDirectory.resolve("escaped-tools");
        Files.createDirectory(escapedDirectory);
        Files.createSymbolicLink(checkout.resolve(".tools"), escapedDirectory);

        GradleResult result = runGradle(checkout, "provisionTlaTools");

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(
                result.output().contains("Destination TLA+ Tools path must not contain symbolic links"),
                result.output());
        assertFalse(Files.exists(escapedDirectory.resolve("tla2tools-1.8.0.jar")));
    }

    private Path createCheckout(String name) throws IOException {
        Path checkout = temporaryDirectory.resolve(name);
        Files.createDirectories(checkout);
        copyFile("build.gradle.kts", checkout);
        copyFile("settings.gradle.kts", checkout);
        copyFile("gradlew", checkout);
        copyTree("gradle", checkout);
        copyTree("config", checkout);
        copyTree("vendor", checkout);
        return checkout;
    }

    private static void copyFile(String relativePath, Path checkout) throws IOException {
        Path source = PROJECT_ROOT.resolve(relativePath);
        Path destination = checkout.resolve(relativePath);
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void copyTree(String relativePath, Path checkout) throws IOException {
        Path sourceRoot = PROJECT_ROOT.resolve(relativePath);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                Path destination = checkout.resolve(relativePath).resolve(sourceRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static GradleResult runGradle(Path checkout, String task) throws Exception {
        Path outputFile = checkout.resolve("gradle-output.log");
        List<String> command = List.of(
                "bash",
                "./gradlew",
                task,
                "--offline",
                "--no-daemon",
                "--no-build-cache",
                "--rerun-tasks",
                "--console=plain");
        Process process = new ProcessBuilder(command)
                .directory(checkout.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            throw new AssertionError("Gradle process exceeded " + PROCESS_TIMEOUT);
        }
        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        return new GradleResult(process.exitValue(), output);
    }

    private static void replaceVendorPath(Path checkout, String replacement) throws IOException {
        Path lock = checkout.resolve("config/tla-tools.lock.json");
        String contents = Files.readString(lock, StandardCharsets.UTF_8);
        Files.writeString(lock, contents.replace(VENDOR_PATH, replacement), StandardCharsets.UTF_8);
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private record GradleResult(int exitCode, String output) {}
}
