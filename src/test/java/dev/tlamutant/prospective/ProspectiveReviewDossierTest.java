package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.experiment.GcdTerminalOutcome;
import dev.tlamutant.experiment.MutationResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveReviewDossierTest {
    private static final CensusInputIdentity.Artifact PROTOCOL = new CensusInputIdentity.Artifact("1".repeat(64), 10);
    private static final CensusInputIdentity.Artifact MANIFEST = new CensusInputIdentity.Artifact("2".repeat(64), 20);
    private static final CensusInputIdentity.Artifact DISCOVERY = new CensusInputIdentity.Artifact("3".repeat(64), 30);

    @Test
    void derivesEveryAndOnlyEligibleCandidateInCanonicalKeyOrder() {
        ProspectiveDiscoveryReport report = discovery();
        ProspectiveDiscoveryReportTest.Fixture fixture = ProspectiveDiscoveryReportTest.fixture();

        ProspectiveReviewDossier dossier =
                ProspectiveReviewDossier.derive(fixture.manifest(), report, PROTOCOL, MANIFEST, DISCOVERY);

        assertEquals(
                List.of(new ProspectiveReviewDossier.CandidateKey("entry-0", "gcd-00000000000000000000")),
                dossier.candidates().stream()
                        .map(ProspectiveReviewDossier.Candidate::key)
                        .toList());
        assertEquals(report.censusArtifact(), dossier.censusArtifact());
        assertEquals("{x |-> 2}", dossier.candidates().getFirst().mutantOnlyWitness());
        assertEquals(List.of("TypeOK"), dossier.candidates().getFirst().typeInvariants());
        assertEquals(List.of("Safety"), dossier.candidates().getFirst().legacyTargetInvariants());
    }

    @Test
    void excludesEachNegatedEligibilityCondition() {
        MutationResult eligible = discovery().gcdOutcomes().getFirst().result();
        List<MutationResult> excluded = List.of(
                changed(eligible, "TIMEOUT", "PASSED", "STATE_EXPANDING", "state-invariant-gap-candidate", "w"),
                changed(
                        eligible,
                        "COMPLETED",
                        "KILLED_STATE_SAFETY",
                        "STATE_EXPANDING",
                        "state-invariant-gap-candidate",
                        "w"),
                changed(eligible, "COMPLETED", "PASSED", "STATE_EQUAL", "state-invariant-gap-candidate", "w"),
                changed(eligible, "COMPLETED", "PASSED", "STATE_EXPANDING", "survived", "w"),
                changed(eligible, "COMPLETED", "PASSED", "STATE_EXPANDING", "state-invariant-gap-candidate", null));

        for (MutationResult result : excluded) {
            assertEquals(false, ProspectiveReviewDossier.isEligible(result));
        }
        assertEquals(true, ProspectiveReviewDossier.isEligible(eligible));
    }

    @Test
    void rejectsDuplicateCandidateKeys() {
        ProspectiveDiscoveryReport report = discovery();
        List<GcdTerminalOutcome> outcomes = new ArrayList<>(report.gcdOutcomes());
        outcomes.add(outcomes.getFirst());
        ProspectiveDiscoveryReport duplicate = new ProspectiveDiscoveryReport(
                report.schemaVersion(),
                report.profile(),
                report.repository(),
                report.commit(),
                report.discoveryReceipt(),
                report.experimentReceipt(),
                report.censusArtifact(),
                report.specifications(),
                outcomes,
                new ProspectiveDiscoveryReport.DiscoverySummary(
                        report.summary().specifications(),
                        report.summary().censusCompletedSpecifications(),
                        report.summary().censusCensoredSpecifications(),
                        report.summary().experimentCompletedSpecifications(),
                        report.summary().experimentCensoredSpecifications(),
                        report.summary().censusSites() + 1,
                        report.summary().completedGcdOutcomes() + 1,
                        report.summary().unresolvedGcdOutcomes(),
                        report.summary().eligibleCandidates() + 1),
                report.census(),
                report.experiment());

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveReviewDossier.derive(
                        ProspectiveDiscoveryReportTest.fixture().manifest(), duplicate, PROTOCOL, MANIFEST, DISCOVERY));
    }

    private static ProspectiveDiscoveryReport discovery() {
        ProspectiveDiscoveryReportTest.Fixture fixture = ProspectiveDiscoveryReportTest.fixture();
        return ProspectiveDiscoveryReport.create(
                fixture.manifest(),
                fixture.census(),
                new CensusInputIdentity.Artifact("b".repeat(64), 20),
                new CensusInputIdentity.Artifact("a".repeat(64), 10),
                fixture.experiment());
    }

    private static MutationResult changed(
            MutationResult original, String execution, String oracle, String relation, String verdict, String witness) {
        return new MutationResult(
                original.id(),
                original.family(),
                original.definition(),
                original.original(),
                original.replacement(),
                execution,
                oracle,
                relation,
                verdict,
                original.diagnosticReason(),
                original.baselineStateCount(),
                original.mutantStateCount(),
                witness,
                original.attribution());
    }
}
