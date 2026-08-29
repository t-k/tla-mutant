package dev.tlamutant.prospective;

import dev.tlamutant.security.BoundedFileSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProspectiveClosureProvenance {
    private ProspectiveClosureProvenance() {}

    public static List<ProspectiveClosureAssessment.InputArtifact> create(
            Map<String, BoundedFileSnapshot> receiptInputs,
            BoundedFileSnapshot validationReceipt,
            BoundedFileSnapshot result,
            BoundedFileSnapshot validatorApplication) {
        Objects.requireNonNull(receiptInputs, "receiptInputs");
        if (!receiptInputs.keySet().equals(ProspectiveReceipt.INTERVENTION_EXPERIMENT_ROLES)) {
            throw new IllegalArgumentException("Prospective closure provenance input roles are incomplete");
        }
        Map<String, BoundedFileSnapshot> all = new HashMap<>(receiptInputs);
        all.put("validation-receipt", Objects.requireNonNull(validationReceipt, "validationReceipt"));
        all.put("v11-result", Objects.requireNonNull(result, "result"));
        all.put("validator-application", Objects.requireNonNull(validatorApplication, "validatorApplication"));
        return ProspectiveClosureAssessment.requiredInputRoles().stream()
                .map(role -> artifact(role, all.get(role)))
                .toList();
    }

    private static ProspectiveClosureAssessment.InputArtifact artifact(String role, BoundedFileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot for role " + role);
        return new ProspectiveClosureAssessment.InputArtifact(role, snapshot.sha256(), snapshot.sizeBytes());
    }
}
