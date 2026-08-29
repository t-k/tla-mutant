package dev.tlamutant.prospective;

import java.util.List;
import java.util.Objects;

public record ProspectiveClosureAssessment(
        int schemaVersion,
        String profile,
        String reviewerMode,
        String closureStatus,
        int selected,
        int closed,
        int rejectedByBaseline,
        int unresolved,
        List<InputArtifact> inputArtifacts,
        List<Outcome> outcomes) {
    private static final List<String> REQUIRED_INPUT_ROLES = List.of(
            "application",
            "census-receipt",
            "dossier",
            "policy",
            "review",
            "tools",
            "v10-census",
            "v10-discovery",
            "v10-discovery-receipt",
            "v10-experiment-receipt",
            "v10-manifest",
            "v10-protocol",
            "v11-census",
            "v11-manifest",
            "v11-protocol",
            "v11-result",
            "validation-receipt",
            "validator-application");

    public ProspectiveClosureAssessment {
        if (schemaVersion != 2
                || !"gcd-prospective-v11".equals(profile)
                || !ProspectiveProtocol.REVIEWER_MODE.equals(reviewerMode)
                || !java.util.Set.of("VERIFIED", "UNRESOLVED").contains(closureStatus)) {
            throw new IllegalArgumentException("Prospective closure assessment identity or status is invalid");
        }
        inputArtifacts = List.copyOf(Objects.requireNonNull(inputArtifacts, "inputArtifacts"));
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        if (!inputArtifacts.stream().map(InputArtifact::role).toList().equals(REQUIRED_INPUT_ROLES)) {
            throw new IllegalArgumentException("Prospective closure input artifact roles are not canonical");
        }
        if (selected <= 0
                || closed < 0
                || rejectedByBaseline < 0
                || unresolved < 0
                || selected != outcomes.size()
                || selected != closed + rejectedByBaseline + unresolved
                || closureStatus.equals("VERIFIED") != (closed == selected)) {
            throw new IllegalArgumentException("Prospective closure totals do not conserve selected interventions");
        }
    }

    public static List<String> requiredInputRoles() {
        return REQUIRED_INPUT_ROLES;
    }

    public record InputArtifact(String role, String sha256, long sizeBytes) {
        public InputArtifact {
            if (role == null
                    || !role.matches("[a-z0-9][a-z0-9-]{0,63}")
                    || sha256 == null
                    || !sha256.matches("[0-9a-f]{64}")
                    || sizeBytes <= 0) {
                throw new IllegalArgumentException("Prospective closure input artifact is invalid");
            }
        }
    }

    public record Outcome(
            ProspectiveReviewDossier.CandidateKey key,
            String status,
            String reason,
            int baselineStateCount,
            int sourceMutantStateCount,
            Integer typeOnlyStateCount,
            String sourceStateRelation,
            String typeOnlyStateRelation,
            String sourceMutantOnlyWitness,
            String typeOnlyMutantOnlyWitness,
            String verdict) {
        public Outcome {
            Objects.requireNonNull(key, "key");
            if (!java.util.Set.of("CLOSED", "REJECTED_BY_BASELINE", "UNRESOLVED")
                    .contains(status)) {
                throw new IllegalArgumentException("Prospective closure outcome status is invalid");
            }
            ProspectiveText.requireSafeLine(reason, "closure reason", 500);
            if (baselineStateCount < 0
                    || sourceMutantStateCount < 0
                    || typeOnlyStateCount != null && typeOnlyStateCount < 0) {
                throw new IllegalArgumentException("Prospective closure state counts must not be negative");
            }
        }
    }
}
