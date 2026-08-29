package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveAssessmentTest {
    @Test
    void reportsPositiveDiscoverySeparatelyFromPendingClosure() throws Exception {
        ProspectiveReviewDossier dossier = ProspectiveReviewValidatorTest.dossier(1);
        ProspectiveReviewLedger ledger = ProspectiveReviewValidatorTest.ledger(
                dossier,
                List.of(ProspectiveReviewValidatorTest.accept(
                        dossier.candidates().getFirst().key())));
        ProspectiveReviewValidator.ValidatedReview review = ProspectiveReviewValidator.validate(
                dossier, new dev.tlamutant.census.CensusInputIdentity.Artifact("9".repeat(64), 90), ledger, 3);

        ProspectiveAssessment assessment = ProspectiveAssessment.review(dossier, review);

        assertEquals("POSITIVE", assessment.discoveryStatus());
        assertEquals("PENDING_INTERVENTION", assessment.closureStatus());
        assertEquals(1, assessment.eligible());
        assertEquals(1, assessment.reviewed());
        assertEquals(1, assessment.accepted());
    }

    @Test
    void permitsAnExactEmptyReviewAndReportsNegativeDiscovery() {
        ProspectiveReviewDossier one = ProspectiveReviewValidatorTest.dossier(1);
        ProspectiveReviewDossier empty = new ProspectiveReviewDossier(
                1,
                one.profile(),
                one.protocolArtifact(),
                one.manifestArtifact(),
                one.censusArtifact(),
                one.discoveryArtifact(),
                List.of());
        ProspectiveReviewLedger ledger = ProspectiveReviewValidatorTest.ledger(empty, List.of());
        ProspectiveReviewValidator.ValidatedReview review = ProspectiveReviewValidator.validate(
                empty, new dev.tlamutant.census.CensusInputIdentity.Artifact("9".repeat(64), 90), ledger, 3);

        ProspectiveAssessment assessment = ProspectiveAssessment.review(empty, review);

        assertEquals("NEGATIVE", assessment.discoveryStatus());
        assertEquals("NOT_AUTHORIZED", assessment.closureStatus());
        assertEquals(0, assessment.reviewed());
    }
}
