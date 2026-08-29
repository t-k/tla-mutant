package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.security.BoundedFileSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProspectiveClosureProvenanceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void derivesCanonicalIdentitiesFromEveryExactInputSnapshot() throws Exception {
        Map<String, BoundedFileSnapshot> receiptInputs = receiptInputs();
        BoundedFileSnapshot validationReceipt = snapshot("validation-receipt", "validation receipt");
        BoundedFileSnapshot result = snapshot("v11-result", "corrected result");
        BoundedFileSnapshot validator = snapshot("validator-application", "validator jar");

        List<ProspectiveClosureAssessment.InputArtifact> actual =
                ProspectiveClosureProvenance.create(receiptInputs, validationReceipt, result, validator);

        assertEquals(
                ProspectiveClosureAssessment.requiredInputRoles(),
                actual.stream()
                        .map(ProspectiveClosureAssessment.InputArtifact::role)
                        .toList());
        assertIdentity(receiptInputs.get("application"), artifact(actual, "application"));
        assertIdentity(validationReceipt, artifact(actual, "validation-receipt"));
        assertIdentity(result, artifact(actual, "v11-result"));
        assertIdentity(validator, artifact(actual, "validator-application"));
    }

    @Test
    void rejectsAnIncompleteReceiptInputSet() throws Exception {
        Map<String, BoundedFileSnapshot> receiptInputs = receiptInputs();
        receiptInputs.remove("review");

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveClosureProvenance.create(
                        receiptInputs,
                        snapshot("validation-receipt", "validation receipt"),
                        snapshot("v11-result", "corrected result"),
                        snapshot("validator-application", "validator jar")));
    }

    private Map<String, BoundedFileSnapshot> receiptInputs() throws Exception {
        Map<String, BoundedFileSnapshot> snapshots = new HashMap<>();
        for (String role : ProspectiveReceipt.INTERVENTION_EXPERIMENT_ROLES) {
            snapshots.put(role, snapshot(role, "bytes for " + role));
        }
        return snapshots;
    }

    private BoundedFileSnapshot snapshot(String name, String value) throws Exception {
        Path path = temporaryDirectory.resolve(name + ".bin");
        Files.writeString(path, value);
        return BoundedFileSnapshot.read(path, 1024);
    }

    private static ProspectiveClosureAssessment.InputArtifact artifact(
            List<ProspectiveClosureAssessment.InputArtifact> artifacts, String role) {
        return artifacts.stream()
                .filter(artifact -> artifact.role().equals(role))
                .findFirst()
                .orElseThrow();
    }

    private static void assertIdentity(
            BoundedFileSnapshot expected, ProspectiveClosureAssessment.InputArtifact actual) {
        assertEquals(expected.sha256(), actual.sha256());
        assertEquals(expected.sizeBytes(), actual.sizeBytes());
    }
}
