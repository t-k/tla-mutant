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
        List<Outcome> outcomes) {
    public ProspectiveClosureAssessment {
        if (schemaVersion != 1
                || !"gcd-prospective-v11".equals(profile)
                || !ProspectiveProtocol.REVIEWER_MODE.equals(reviewerMode)
                || !java.util.Set.of("VERIFIED", "UNRESOLVED").contains(closureStatus)) {
            throw new IllegalArgumentException("Prospective closure assessment identity or status is invalid");
        }
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
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
