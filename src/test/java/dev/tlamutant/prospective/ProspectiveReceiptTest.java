package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProspectiveReceiptTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyTheExactDiscoveryRoleSet() {
        ProspectiveReceipt receipt = receipt("discovery", ProspectiveReceipt.DISCOVERY_ROLES);

        assertEquals(
                ProspectiveReceipt.DISCOVERY_ROLES,
                receipt.artifacts().stream()
                        .map(ProspectiveReceipt.Artifact::role)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @Test
    void acceptsOnlyTheExactExperimentRoleSet() {
        ProspectiveReceipt receipt = receipt("experiment", ProspectiveReceipt.EXPERIMENT_ROLES);

        assertTrue(ProspectiveReceipt.EXPERIMENT_ROLES.contains("discovery-receipt"));
        assertEquals(
                ProspectiveReceipt.EXPERIMENT_ROLES,
                receipt.artifacts().stream()
                        .map(ProspectiveReceipt.Artifact::role)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @Test
    void rejectsMissingExtraDuplicateAndInvalidArtifactIdentity() {
        List<ProspectiveReceipt.Artifact> missing = artifacts(ProspectiveReceipt.DISCOVERY_ROLES);
        missing.removeLast();
        List<ProspectiveReceipt.Artifact> extra = artifacts(ProspectiveReceipt.DISCOVERY_ROLES);
        extra.add(new ProspectiveReceipt.Artifact("unexpected", "a".repeat(64), 1));
        List<ProspectiveReceipt.Artifact> duplicate = artifacts(ProspectiveReceipt.DISCOVERY_ROLES);
        duplicate.set(1, duplicate.getFirst());

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveReceipt(1, "discovery", "gcd-prospective-v10", missing));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveReceipt(1, "discovery", "gcd-prospective-v10", extra));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveReceipt(1, "discovery", "gcd-prospective-v10", duplicate));
        assertThrows(
                IllegalArgumentException.class, () -> new ProspectiveReceipt.Artifact("registry", "A".repeat(64), 1));
        assertThrows(
                IllegalArgumentException.class, () -> new ProspectiveReceipt.Artifact("registry", "a".repeat(64), 0));
    }

    @Test
    void validatesEveryIdentityBeforeParsingOrCreatingOutputs() throws Exception {
        Path receiptPath = temporaryDirectory.resolve("receipt.json");
        Path registry = write("registry.json", "not-json");
        Path protocol = write("protocol.json", "not-json");
        Path manifest = write("manifest.json", "malformed-public-source-metadata");
        Path policy = write("policy.json", "not-json");
        Path tools = write("tools.jar", "tools");
        Path application = write("application.jar", "application");
        Path cache = temporaryDirectory.resolve("cache");
        Path output = temporaryDirectory.resolve("result.json");
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
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(receiptPath.toFile(), wrong);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ProspectivePreflight()
                .validateDiscovery(new ProspectivePreflight.DiscoveryPaths(
                        receiptPath,
                        registry,
                        protocol,
                        manifest,
                        policy,
                        tools,
                        application,
                        cache,
                        List.of(output))));

        assertTrue(failure.getMessage().contains("protocol"));
        assertEquals(false, Files.exists(cache));
        assertEquals(false, Files.exists(output));
    }

    @Test
    void rejectsInputAliasesAndOutputCacheContainmentBeforeParsing() throws Exception {
        Path input = write("input.json", "x");
        Path receiptPath = write("receipt.json", "x");
        Path cache = temporaryDirectory.resolve("cache");
        Path output = cache.resolve("result.json");
        ProspectivePreflight.DiscoveryPaths aliased = new ProspectivePreflight.DiscoveryPaths(
                receiptPath, input, input, input, input, input, input, cache, List.of(output));

        assertThrows(IllegalArgumentException.class, () -> new ProspectivePreflight().validateDiscovery(aliased));
        assertEquals(false, Files.exists(cache));
    }

    @Test
    void rejectsOutputCacheContainmentThroughAnExistingAncestorSymlink() throws Exception {
        Path realCache = Files.createDirectory(temporaryDirectory.resolve("real-cache"));
        Path cacheLink = temporaryDirectory.resolve("cache-link");
        Files.createSymbolicLink(cacheLink, realCache);
        ProspectivePreflight.DiscoveryPaths paths = new ProspectivePreflight.DiscoveryPaths(
                write("receipt-link-test.json", "x"),
                write("registry-link-test.json", "x"),
                write("protocol-link-test.json", "x"),
                write("manifest-link-test.json", "x"),
                write("policy-link-test.json", "x"),
                write("tools-link-test.jar", "x"),
                write("application-link-test.jar", "x"),
                cacheLink,
                List.of(realCache.resolve("result.json")));

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> new ProspectivePreflight().validateDiscovery(paths));

        assertTrue(failure.getMessage().contains("output and cache"));
    }

    @Test
    void rejectsExistingCanonicalOutput() throws Exception {
        Path input = write("input.json", "x");
        Path receiptPath = write("receipt.json", "x");
        Path output = write("result.json", "already exists");
        ProspectivePreflight.DiscoveryPaths paths = new ProspectivePreflight.DiscoveryPaths(
                receiptPath,
                input,
                input,
                input,
                input,
                input,
                input,
                temporaryDirectory.resolve("cache"),
                List.of(output));

        assertThrows(IllegalArgumentException.class, () -> new ProspectivePreflight().validateDiscovery(paths));
        assertDoesNotThrow(() -> Files.readString(output));
    }

    @Test
    void bindsTheExperimentCensusToTheSuppliedDiscoveryReceiptBytes() throws Exception {
        Path discoveryReceipt = write("discovery-receipt.json", "frozen receipt bytes");
        dev.tlamutant.security.BoundedFileSnapshot snapshot =
                dev.tlamutant.security.BoundedFileSnapshot.read(discoveryReceipt, 1024 * 1024);
        ProspectiveCensusReport fixture =
                ProspectiveDiscoveryReportTest.fixture().census();
        ProspectiveCensusReport matching = new ProspectiveCensusReport(
                1,
                new dev.tlamutant.census.CensusInputIdentity.Artifact(snapshot.sha256(), snapshot.sizeBytes()),
                fixture.census());

        assertDoesNotThrow(() -> ProspectivePreflight.validateCensusDiscoveryReceipt(snapshot, matching));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectivePreflight.validateCensusDiscoveryReceipt(snapshot, fixture));
    }

    private ProspectiveReceipt receipt(String phase, java.util.Set<String> roles) {
        return new ProspectiveReceipt(1, phase, "gcd-prospective-v10", artifacts(roles));
    }

    private static List<ProspectiveReceipt.Artifact> artifacts(java.util.Set<String> roles) {
        List<ProspectiveReceipt.Artifact> artifacts = new ArrayList<>();
        int index = 1;
        for (String role : roles.stream().sorted().toList()) {
            artifacts.add(new ProspectiveReceipt.Artifact(
                    role, Integer.toHexString(index).repeat(64), index));
            index++;
        }
        return artifacts;
    }

    private Path write(String fileName, String text) throws Exception {
        Path path = temporaryDirectory.resolve(fileName);
        Files.writeString(path, text);
        return path;
    }

    private static ProspectiveReceipt.Artifact artifact(String role, Path path) throws Exception {
        dev.tlamutant.security.BoundedFileSnapshot snapshot =
                dev.tlamutant.security.BoundedFileSnapshot.read(path, 1024 * 1024);
        return new ProspectiveReceipt.Artifact(role, snapshot.sha256(), snapshot.sizeBytes());
    }
}
