package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.security.BoundedFileSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProspectiveCliPreflightIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsReceiptMismatchBeforeParsingPublicMetadataOrCreatingExecutionPaths() throws Exception {
        Path registry = write("registry.json", "not-json");
        Path protocol = write("protocol.json", "not-json");
        Path manifest = write("manifest.json", "malformed-public-source-metadata");
        Path policy = write("policy.json", "not-json");
        Path tools = write("tools.jar", "not-a-tools-jar");
        Path application =
                Path.of("build/libs/tla-mutant-all.jar").toAbsolutePath().normalize();
        Path receipt = temporaryDirectory.resolve("receipt.json");
        Path cache = temporaryDirectory.resolve("cache");
        Path census = temporaryDirectory.resolve("census.json");
        Path failed = temporaryDirectory.resolve("failed.json");
        ProspectiveReceipt wrong = new ProspectiveReceipt(
                1,
                "discovery",
                "gcd-prospective-v10",
                List.of(
                        artifact("registry", registry),
                        new ProspectiveReceipt.Artifact("protocol", "0".repeat(64), Files.size(protocol)),
                        artifact("manifest", manifest),
                        artifact("policy", policy),
                        artifact("tools", tools),
                        artifact("application", application)));
        new ObjectMapper().writeValue(receipt.toFile(), wrong);

        Process process = new ProcessBuilder(
                        javaBinary(),
                        "-jar",
                        application.toString(),
                        "prospective-census",
                        "--registry",
                        registry.toString(),
                        "--protocol",
                        protocol.toString(),
                        "--manifest",
                        manifest.toString(),
                        "--receipt",
                        receipt.toString(),
                        "--policy",
                        policy.toString(),
                        "--tools-jar",
                        tools.toString(),
                        "--cache-dir",
                        cache.toString(),
                        "--census-report",
                        census.toString(),
                        "--failed-census-report",
                        failed.toString())
                .redirectErrorStream(true)
                .start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

        assertNotEquals(0, process.exitValue(), output);
        assertTrue(output.contains("Prospective receipt identity mismatch for role: protocol"), output);
        assertFalse(Files.exists(cache));
        assertFalse(Files.exists(census));
        assertFalse(Files.exists(failed));
        assertEquals("malformed-public-source-metadata", Files.readString(manifest));
    }

    private Path write(String name, String value) throws Exception {
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, value);
        return path;
    }

    private static ProspectiveReceipt.Artifact artifact(String role, Path path) throws Exception {
        BoundedFileSnapshot snapshot = BoundedFileSnapshot.read(path, 128L * 1024 * 1024);
        return new ProspectiveReceipt.Artifact(role, snapshot.sha256(), snapshot.sizeBytes());
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
