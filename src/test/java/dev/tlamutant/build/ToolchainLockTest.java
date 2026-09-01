package dev.tlamutant.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

final class ToolchainLockTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path LOCK = PROJECT_ROOT.resolve("config/tla-tools.lock.json");
    private static final String EXPECTED_VENDOR_PATH = "vendor/tla-tools/1.8.0-9787e65/tla2tools.jar";
    private static final String EXPECTED_UPSTREAM_URL =
            "https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar";
    private static final String EXPECTED_UPSTREAM_REVISION = "9787e65714c37d94eebab40774bff401bd9f616d";
    private static final long EXPECTED_SIZE = 4_487_757L;
    private static final String EXPECTED_SHA256 = "eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a";

    @Test
    void pinsTheReviewedTlaToolsArtifact() throws Exception {
        JsonNode lock = new ObjectMapper().readTree(Files.readString(LOCK));

        assertEquals("1.8.0", lock.required("version").textValue());
        assertEquals(EXPECTED_VENDOR_PATH, lock.required("vendorPath").textValue());
        assertEquals(EXPECTED_UPSTREAM_URL, lock.required("upstreamUrl").textValue());
        assertEquals(
                EXPECTED_UPSTREAM_REVISION, lock.required("upstreamRevision").textValue());
        assertEquals(EXPECTED_SIZE, lock.required("sizeBytes").longValue());
        assertEquals(EXPECTED_SHA256, lock.required("sha256").textValue());

        Path vendoredJar =
                PROJECT_ROOT.resolve(lock.required("vendorPath").textValue()).normalize();
        assertTrue(vendoredJar.startsWith(PROJECT_ROOT));
        assertFalse(Files.isSymbolicLink(vendoredJar));
        assertTrue(Files.isRegularFile(vendoredJar, LinkOption.NOFOLLOW_LINKS));
        assertEquals(EXPECTED_SIZE, Files.size(vendoredJar));
        assertEquals(
                EXPECTED_SHA256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(vendoredJar))));
    }

    @Test
    void preservesUpstreamManifestLicensesAndUnsignedStatus() throws Exception {
        Path vendoredJar = PROJECT_ROOT.resolve(EXPECTED_VENDOR_PATH);

        try (JarFile jar = new JarFile(vendoredJar.toFile())) {
            assertNotNull(jar.getJarEntry("META-INF/LICENSE.md"));
            assertNotNull(jar.getJarEntry("License.txt"));
            assertEquals(
                    EXPECTED_UPSTREAM_REVISION,
                    jar.getManifest().getMainAttributes().getValue("X-Git-Revision"));
            assertEquals("tlc2.TLC", jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertFalse(jar.stream()
                    .map(entry -> entry.getName().toUpperCase(Locale.ROOT))
                    .anyMatch(name -> name.startsWith("META-INF/")
                            && (name.endsWith(".SF")
                                    || name.endsWith(".RSA")
                                    || name.endsWith(".DSA")
                                    || name.endsWith(".EC")
                                    || name.substring("META-INF/".length()).startsWith("SIG-"))));
        }
    }

    @Test
    void buildScriptProvisionsTheVendoredArtifactWithoutNetworkCode() throws IOException {
        String script = Files.readString(PROJECT_ROOT.resolve("build.gradle.kts"));

        assertTrue(script.contains("vendorPath"));
        assertTrue(script.contains("upstreamRevision"));
        assertTrue(script.contains("provisionTlaTools"));
        assertTrue(script.contains("NOFOLLOW_LINKS"));
        assertTrue(script.contains("ATOMIC_MOVE"));
        assertTrue(script.contains("val vendoredToolsRoot"));
        assertTrue(script.contains("inputs.dir(vendoredToolsRoot)"));
        assertFalse(script.contains("inputs.file(vendoredToolsJar)"));
        assertTrue(script.contains("getPosixFilePermissions"));
        assertFalse(script.contains("runCatching"));
        assertTrue(script.contains("$label path must not contain symbolic links"));
        assertTrue(script.contains(
                "requireNoSymbolicLinkComponents(projectRoot, destination, \"Destination TLA+ Tools\")"));
        assertTrue(script.contains("dependsOn(provisionTlaTools)"));
        assertFalse(script.contains("java.net.URI"));
        assertFalse(script.contains("java.net.http.HttpClient"));
        assertFalse(script.contains("java.net.http.HttpRequest"));
        assertFalse(script.contains("java.net.http.HttpResponse"));
    }

    @Test
    void aChangedDigestCannotMatchTheReviewedArtifact() {
        assertNotEquals(EXPECTED_SHA256, "0abd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a");
    }
}
