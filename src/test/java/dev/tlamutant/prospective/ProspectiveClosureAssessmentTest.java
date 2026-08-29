package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveClosureAssessmentTest {
    @Test
    void acceptsExactlyTheCanonicalClosureInputRoles() {
        List<ProspectiveClosureAssessment.InputArtifact> inputs = validInputs();

        ProspectiveClosureAssessment assessment = assessment(inputs);

        assertEquals(2, assessment.schemaVersion());
        assertEquals(inputs, assessment.inputArtifacts());
        assertEquals(
                ProspectiveClosureAssessment.requiredInputRoles(),
                assessment.inputArtifacts().stream()
                        .map(ProspectiveClosureAssessment.InputArtifact::role)
                        .toList());
    }

    @Test
    void rejectsAnIncompleteUnexpectedDuplicateOrNoncanonicalProvenanceSet() {
        List<ProspectiveClosureAssessment.InputArtifact> missing = new ArrayList<>(validInputs());
        missing.removeLast();

        List<ProspectiveClosureAssessment.InputArtifact> unexpected = new ArrayList<>(validInputs());
        unexpected.set(
                unexpected.size() - 1, new ProspectiveClosureAssessment.InputArtifact("unexpected", "0".repeat(64), 1));

        List<ProspectiveClosureAssessment.InputArtifact> duplicate = new ArrayList<>(validInputs());
        duplicate.set(1, duplicate.getFirst());

        List<ProspectiveClosureAssessment.InputArtifact> reversed = new ArrayList<>(validInputs());
        Collections.reverse(reversed);

        assertThrows(IllegalArgumentException.class, () -> assessment(missing));
        assertThrows(IllegalArgumentException.class, () -> assessment(unexpected));
        assertThrows(IllegalArgumentException.class, () -> assessment(duplicate));
        assertThrows(IllegalArgumentException.class, () -> assessment(reversed));
    }

    @Test
    void rejectsMalformedArtifactIdentities() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveClosureAssessment.InputArtifact("application", "A".repeat(64), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveClosureAssessment.InputArtifact("application", "0".repeat(64), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveClosureAssessment.InputArtifact("Application", "0".repeat(64), 1));
    }

    private static ProspectiveClosureAssessment assessment(
            List<ProspectiveClosureAssessment.InputArtifact> inputArtifacts) {
        ProspectiveClosureAssessment.Outcome outcome = new ProspectiveClosureAssessment.Outcome(
                new ProspectiveReviewDossier.CandidateKey("nbacg-guer01", "gcd-3c1ee8b4429ee7af9c2f"),
                "CLOSED",
                "All preregistered closure conditions passed",
                24_922,
                30_943,
                30_943,
                "STATE_EXPANDING",
                "STATE_EXPANDING",
                "witness",
                "witness",
                "adopted-target-invariant-kill");
        return new ProspectiveClosureAssessment(
                2,
                "gcd-prospective-v11",
                ProspectiveProtocol.REVIEWER_MODE,
                "VERIFIED",
                1,
                1,
                0,
                0,
                inputArtifacts,
                List.of(outcome));
    }

    private static List<ProspectiveClosureAssessment.InputArtifact> validInputs() {
        return ProspectiveClosureAssessment.requiredInputRoles().stream()
                .map(role -> new ProspectiveClosureAssessment.InputArtifact(role, "0".repeat(64), 1))
                .toList();
    }
}
