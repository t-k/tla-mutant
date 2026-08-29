package dev.tlamutant.prospective;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProspectiveAssessment(
        int schemaVersion,
        String reviewerMode,
        String discoveryStatus,
        String closureStatus,
        int eligible,
        int reviewed,
        int accepted,
        int acceptedOverflow,
        List<ProspectiveReviewDossier.CandidateKey> selectedInterventions,
        Map<String, Integer> terminalCounts,
        String limitation) {
    public ProspectiveAssessment {
        if (schemaVersion != 1 || !ProspectiveProtocol.REVIEWER_MODE.equals(reviewerMode)) {
            throw new IllegalArgumentException("Prospective assessment identity is invalid");
        }
        if (!java.util.Set.of("POSITIVE", "NEGATIVE").contains(discoveryStatus)
                || !java.util.Set.of("PENDING_INTERVENTION", "NOT_AUTHORIZED").contains(closureStatus)) {
            throw new IllegalArgumentException("Prospective assessment status is invalid");
        }
        selectedInterventions = List.copyOf(Objects.requireNonNull(selectedInterventions, "selectedInterventions"));
        terminalCounts = Map.copyOf(Objects.requireNonNull(terminalCounts, "terminalCounts"));
        ProspectiveText.requireSafeLine(limitation, "limitation", 500);
        if (reviewed != eligible
                || accepted < 0
                || accepted > reviewed
                || acceptedOverflow != accepted - selectedInterventions.size()
                || (accepted == 0) != discoveryStatus.equals("NEGATIVE")
                || (accepted == 0) != closureStatus.equals("NOT_AUTHORIZED")) {
            throw new IllegalArgumentException("Prospective assessment totals or statuses do not conserve review");
        }
    }

    public static ProspectiveAssessment review(
            ProspectiveReviewDossier dossier, ProspectiveReviewValidator.ValidatedReview review) {
        Objects.requireNonNull(dossier, "dossier");
        Objects.requireNonNull(review, "review");
        if (dossier.candidates().size() != review.eligible()) {
            throw new IllegalArgumentException("Prospective assessment dossier differs from validated review");
        }
        boolean positive = review.accepted() > 0;
        return new ProspectiveAssessment(
                1,
                ProspectiveProtocol.REVIEWER_MODE,
                positive ? "POSITIVE" : "NEGATIVE",
                positive ? "PENDING_INTERVENTION" : "NOT_AUTHORIZED",
                review.eligible(),
                review.reviewed(),
                review.accepted(),
                review.acceptedOverflow(),
                review.selectedInterventions(),
                review.terminalCounts(),
                "Usefulness was judged by a user-delegated agent under a finite model, not by an external domain owner.");
    }
}
