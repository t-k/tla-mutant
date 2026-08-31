package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.security.Digests;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class HistoricalArtifactFreezeIntegrationTest {
    private static final long MAXIMUM_BYTES = 1_000_000;

    @Test
    void preservesEveryFrozenV8AndV9BoundaryArtifactByte() {
        Map<String, Identity> expected = new LinkedHashMap<>();
        expected.put(
                "corpus/gcd-stratified-manifest.json",
                new Identity("1caeb7734ea814e0d9d2e007ae0711f7fa9dcc332e2cce50b7fb780b08e5ec7e", 17_202));
        expected.put(
                "docs/experiments/gcd-stratified-census.json",
                new Identity("6de7c400622557a545a82c3db0d29b20f304ab2e0c47ec29eddfea2e9a72df95", 24_623));
        expected.put(
                "docs/experiments/gcd-stratified.json",
                new Identity("12de6c80837a4871ea09a63173b88bbb6f1bc7c2c4b0826f6f35084230dcfd5e", 119_289));
        expected.put(
                "docs/experiments/gcd-stratified.md",
                new Identity("43ce8e7fa8f5867d4024d8ea9e44df95ce767b6a4aa657ad46ae8743dffc5f7a", 27_807));
        expected.put(
                "corpus/gcd-stratified-manifest-v9.json",
                new Identity("521937e0b3288d50f51e77d162e18d6ef1515ec8b644034c71f2d2ea68522bc4", 17_832));
        expected.put(
                "docs/experiments/gcd-stratified-census-v9.json",
                new Identity("cd714aecfec926c3250922ec52e9dc6caef944c1300ff2cafaf9911c2f7a66cb", 24_623));
        expected.put(
                "docs/experiments/gcd-stratified-v9.json",
                new Identity("aa2cad9943a5216238a1907a233af41dc5d95bb82ffba9655a35f71b8af38ffb", 143_902));
        expected.put(
                "docs/experiments/gcd-stratified-v9.md",
                new Identity("aa36b720a2c0156961e4ff9ecf4eb5357c0d647c858ed41f6d2403c0069688a9", 28_510));
        expected.put(
                "docs/experiments/gcd-stratified-v9-preregistration.md",
                new Identity("04d94443265411a7c8222f2cb002b3dd02cd7c7deb8d0619d5f00bc7b534839e", 2_525));
        expected.put(
                "docs/experiments/gcd-stratified-assessment-v9.md",
                new Identity("a57980bc2dac0e4de96cfdd67cb1948d42a2953da6908d3998aefa7e4176b0f4", 3_906));
        expected.put(
                "docs/experiments/gcd-stratified-human-review.md",
                new Identity("17732c93c31198a881afdc6a8442f67acbe1bc4164ec1d1f995e08024e8e6773", 7_560));

        assertAll(expected.entrySet().stream().map(entry -> () -> assertIdentity(entry.getKey(), entry.getValue())));
    }

    @Test
    void bindsTheCanonicalClosureAssessmentToEveryAvailableInputArtifact() throws Exception {
        Path assessmentPath = Path.of("docs/experiments/gcd-prospective-closure-assessment-v11.json");
        assertIdentity(
                assessmentPath.toString(),
                new Identity("a2d62914b660363af4a79fe5fa9b475636b49faf61f4151b1d90e54f6b6f4c35", 3_890));
        ProspectiveClosureAssessment assessment =
                new ObjectMapper().readValue(assessmentPath.toFile(), ProspectiveClosureAssessment.class);

        assertEquals(2, assessment.schemaVersion());
        assertEquals(
                ProspectiveClosureAssessment.requiredInputRoles(),
                assessment.inputArtifacts().stream()
                        .map(ProspectiveClosureAssessment.InputArtifact::role)
                        .toList());

        Map<String, String> repositoryInputs = new LinkedHashMap<>();
        repositoryInputs.put("census-receipt", "docs/experiments/gcd-prospective-receipt-v11-closure.json");
        repositoryInputs.put("dossier", "docs/experiments/gcd-prospective-review-dossier-v10.json");
        repositoryInputs.put("policy", "config/execution-policy.json");
        repositoryInputs.put("review", "docs/experiments/gcd-prospective-review-v10.json");
        repositoryInputs.put("tools", ".tools/tla2tools-1.8.0.jar");
        repositoryInputs.put("v10-census", "docs/experiments/gcd-prospective-census-v10.json");
        repositoryInputs.put("v10-discovery", "docs/experiments/gcd-prospective-v10.json");
        repositoryInputs.put("v10-discovery-receipt", "docs/experiments/gcd-prospective-receipt-v10.json");
        repositoryInputs.put("v10-experiment-receipt", "docs/experiments/gcd-prospective-experiment-receipt-v10.json");
        repositoryInputs.put("v10-manifest", "corpus/gcd-prospective-manifest-v10.json");
        repositoryInputs.put("v10-protocol", "config/prospective-protocol-v10.json");
        repositoryInputs.put("v11-census", "docs/experiments/gcd-prospective-census-v11-closure.json");
        repositoryInputs.put("v11-manifest", "corpus/gcd-prospective-manifest-v11.json");
        repositoryInputs.put("v11-protocol", "config/prospective-protocol-v11.json");
        repositoryInputs.put("v11-result", "docs/experiments/gcd-prospective-v11-closure.json");
        repositoryInputs.put(
                "validation-receipt", "docs/experiments/gcd-prospective-experiment-receipt-v11-closure.json");
        for (Map.Entry<String, String> entry : repositoryInputs.entrySet()) {
            assertIdentity(entry.getValue(), artifact(assessment, entry.getKey()));
        }

        Path validatorApplication = Path.of(System.getProperty("tla.mutant.test.appJar"));
        assertIdentity(validatorApplication, artifact(assessment, "validator-application"));

        ProspectiveReceipt receipt = new ObjectMapper()
                .readValue(
                        Path.of("docs/experiments/gcd-prospective-experiment-receipt-v11-closure.json")
                                .toFile(),
                        ProspectiveReceipt.class);
        ProspectiveReceipt.Artifact executionApplication = receipt.artifact("application");
        ProspectiveClosureAssessment.InputArtifact assessedApplication = artifact(assessment, "application");
        ProspectiveClosureAssessment.InputArtifact assessedValidator = artifact(assessment, "validator-application");
        assertEquals(executionApplication.sha256(), assessedApplication.sha256());
        assertEquals(executionApplication.sizeBytes(), assessedApplication.sizeBytes());
        assertNotEquals(assessedApplication.sha256(), assessedValidator.sha256());
    }

    private static void assertIdentity(String relativePath, Identity expected) throws Exception {
        Path path = Path.of(relativePath);
        assertEquals(false, Files.isSymbolicLink(path), relativePath + " must not be a symbolic link");
        assertEquals(true, Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), relativePath + " must be regular");
        assertEquals(expected.sizeBytes(), Files.size(path), relativePath + " size");
        assertEquals(expected.sha256(), Digests.sha256(path, MAXIMUM_BYTES), relativePath + " SHA-256");
    }

    private static void assertIdentity(String relativePath, ProspectiveClosureAssessment.InputArtifact expected)
            throws Exception {
        assertIdentity(Path.of(relativePath), expected);
    }

    private static void assertIdentity(Path path, ProspectiveClosureAssessment.InputArtifact expected)
            throws Exception {
        assertEquals(false, Files.isSymbolicLink(path), path + " must not be a symbolic link");
        assertEquals(true, Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path + " must be regular");
        assertEquals(expected.sizeBytes(), Files.size(path), path + " size");
        assertEquals(expected.sha256(), Digests.sha256(path, 128L * 1024 * 1024), path + " SHA-256");
    }

    private static ProspectiveClosureAssessment.InputArtifact artifact(
            ProspectiveClosureAssessment assessment, String role) {
        return assessment.inputArtifacts().stream()
                .filter(artifact -> artifact.role().equals(role))
                .findFirst()
                .orElseThrow();
    }

    private record Identity(String sha256, long sizeBytes) {}
}
