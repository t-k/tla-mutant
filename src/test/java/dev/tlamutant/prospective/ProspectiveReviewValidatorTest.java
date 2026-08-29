package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.config.ExecutionPolicy;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveReviewValidatorTest {
    private static final CensusInputIdentity.Artifact DOSSIER_ARTIFACT =
            new CensusInputIdentity.Artifact("9".repeat(64), 90);

    @Test
    void validatedReviewCannotBeConstructedOutsideTheValidator() {
        assertEquals(
                true,
                java.util.Arrays.stream(ProspectiveReviewValidator.ValidatedReview.class.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
    }

    @Test
    void acceptsAnExactBijectionWithSevenPassingCriteria() throws Exception {
        ProspectiveReviewDossier dossier = dossier(1);
        ProspectiveReviewLedger ledger =
                ledger(dossier, List.of(accept(dossier.candidates().getFirst().key())));

        ProspectiveReviewValidator.ValidatedReview review =
                ProspectiveReviewValidator.validate(dossier, DOSSIER_ARTIFACT, ledger, 3);

        assertEquals(1, review.reviewed());
        assertEquals(1, review.accepted());
        assertEquals(List.of(dossier.candidates().getFirst().key()), review.selectedInterventions());
        assertEquals(0, review.acceptedOverflow());
    }

    @Test
    void rejectsMissingDuplicateExtraAndChangedDossierIdentity() throws Exception {
        ProspectiveReviewDossier dossier = dossier(1);
        ProspectiveReviewLedger.Decision decision =
                reject(dossier.candidates().getFirst().key(), "REJECT_REDUNDANT");
        ProspectiveReviewLedger missing = ledger(dossier, List.of());
        ProspectiveReviewLedger duplicate = ledger(dossier, List.of(decision, decision));
        ProspectiveReviewLedger extra = ledger(
                dossier,
                List.of(
                        decision,
                        reject(
                                new ProspectiveReviewDossier.CandidateKey("entry-extra", "gcd-ffffffffffffffffffff"),
                                "REJECT_REDUNDANT")));
        ProspectiveReviewLedger changedIdentity = new ProspectiveReviewLedger(
                1,
                ProspectiveProtocol.REVIEWER_MODE,
                dossier.protocolArtifact(),
                new CensusInputIdentity.Artifact("8".repeat(64), 90),
                List.of(decision));

        for (ProspectiveReviewLedger invalid : List.of(missing, duplicate, extra, changedIdentity)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ProspectiveReviewValidator.validate(dossier, DOSSIER_ARTIFACT, invalid, 3));
        }
    }

    @Test
    void rejectsAcceptWithAnyFailedCriterionChangedPredicateDigestOrUnsafeEvidence() throws Exception {
        ProspectiveReviewDossier dossier = dossier(1);
        ProspectiveReviewDossier.CandidateKey key =
                dossier.candidates().getFirst().key();
        ProspectiveReviewLedger.Decision valid = accept(key);
        ProspectiveReviewLedger.Criteria failed =
                new ProspectiveReviewLedger.Criteria(true, true, true, true, true, false, true);
        ProspectiveReviewLedger.Decision failedCriterion = new ProspectiveReviewLedger.Decision(
                key, "ACCEPT", valid.operator(), valid.predicate(), valid.predicateSha256(), failed, valid.evidence());
        ProspectiveReviewLedger.Decision wrongDigest = new ProspectiveReviewLedger.Decision(
                key, "ACCEPT", valid.operator(), valid.predicate(), "0".repeat(64), valid.criteria(), valid.evidence());

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveReviewValidator.validate(
                        dossier, DOSSIER_ARTIFACT, ledger(dossier, List.of(failedCriterion)), 3));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveReviewValidator.validate(
                        dossier, DOSSIER_ARTIFACT, ledger(dossier, List.of(wrongDigest)), 3));
        assertThrows(IllegalArgumentException.class, () -> evidence("unsafe\u202Etext"));
    }

    @Test
    void selectsOnlyTheFirstThreeAcceptedKeysAfterReviewingAll() throws Exception {
        ProspectiveReviewDossier dossier = dossier(4);
        List<ProspectiveReviewLedger.Decision> decisions = dossier.candidates().stream()
                .map(candidate -> {
                    try {
                        return accept(candidate.key());
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();

        ProspectiveReviewValidator.ValidatedReview review =
                ProspectiveReviewValidator.validate(dossier, DOSSIER_ARTIFACT, ledger(dossier, decisions), 3);

        assertEquals(4, review.reviewed());
        assertEquals(4, review.accepted());
        assertEquals(3, review.selectedInterventions().size());
        assertEquals(
                dossier.candidates().subList(0, 3).stream()
                        .map(ProspectiveReviewDossier.Candidate::key)
                        .toList(),
                review.selectedInterventions());
        assertEquals(1, review.acceptedOverflow());
    }

    @Test
    void derivesTheInterventionCapFromTheBoundProtocolArtifact() throws Exception {
        ProspectiveProtocol protocol = protocol(3);
        CensusInputIdentity.Artifact protocolArtifact = new CensusInputIdentity.Artifact("4".repeat(64), 40);
        ProspectiveReviewDossier original = dossier(1);
        ProspectiveReviewDossier bound = new ProspectiveReviewDossier(
                1,
                original.profile(),
                protocolArtifact,
                original.manifestArtifact(),
                original.censusArtifact(),
                original.discoveryArtifact(),
                original.candidates());
        ProspectiveReviewLedger ledger =
                ledger(bound, List.of(accept(bound.candidates().getFirst().key())));

        ProspectiveReviewValidator.ValidatedReview review =
                ProspectiveReviewValidator.validate(bound, DOSSIER_ARTIFACT, ledger, protocol, protocolArtifact);

        assertEquals(1, review.selectedInterventions().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveReviewValidator.validate(
                        bound,
                        DOSSIER_ARTIFACT,
                        ledger,
                        protocol,
                        new CensusInputIdentity.Artifact("0".repeat(64), protocolArtifact.sizeBytes())));
    }

    private static ProspectiveProtocol protocol(int interventionCap) {
        ExecutionPolicy.Limits limits = new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90);
        List<ProspectiveProtocol.Entry> entries = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            entries.add(new ProspectiveProtocol.Entry(
                    "entry-" + index,
                    "domain-" + index,
                    ProspectiveProtocol.STANDARD_RISK,
                    "family-" + index,
                    "Spec" + index + ".tla",
                    "Spec" + index + ".cfg",
                    List.of("prospective/Spec" + index + ".tla", "prospective/Spec" + index + ".cfg"),
                    List.of("TypeOK"),
                    List.of("Safety"),
                    limits,
                    "Frozen candidate."));
        }
        return new ProspectiveProtocol(
                1,
                "gcd-prospective-v10",
                ProspectiveProtocol.REPOSITORY,
                "c".repeat(40),
                3,
                0,
                interventionCap,
                new ProspectiveProtocol.HistoricalSet(1, "a".repeat(64), 1, "b".repeat(64)),
                ProspectiveProtocol.requiredEligibility(),
                ProspectiveProtocol.requiredReviewRubric(),
                entries);
    }

    static ProspectiveReviewDossier dossier(int count) {
        ProspectiveDiscoveryReportTest.Fixture fixture = ProspectiveDiscoveryReportTest.fixture();
        ProspectiveDiscoveryReport discovery = ProspectiveDiscoveryReport.create(
                fixture.manifest(),
                fixture.census(),
                new CensusInputIdentity.Artifact("b".repeat(64), 20),
                new CensusInputIdentity.Artifact("a".repeat(64), 10),
                fixture.experiment());
        ProspectiveReviewDossier one = ProspectiveReviewDossier.derive(
                fixture.manifest(),
                discovery,
                new CensusInputIdentity.Artifact("1".repeat(64), 10),
                new CensusInputIdentity.Artifact("2".repeat(64), 20),
                new CensusInputIdentity.Artifact("3".repeat(64), 30));
        if (count == 1) {
            return one;
        }
        ProspectiveReviewDossier.Candidate base = one.candidates().getFirst();
        List<ProspectiveReviewDossier.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            candidates.add(new ProspectiveReviewDossier.Candidate(
                    new ProspectiveReviewDossier.CandidateKey("entry-" + index, "gcd-" + "%020x".formatted(index)),
                    base.domain(),
                    base.riskClass(),
                    base.rootModule(),
                    base.sourceFiles(),
                    base.typeInvariants(),
                    base.legacyTargetInvariants(),
                    base.definition(),
                    base.originalGuard(),
                    base.replacement(),
                    base.executionStatus(),
                    base.oracleOutcome(),
                    base.stateRelation(),
                    base.verdict(),
                    base.baselineStateCount(),
                    base.mutantStateCount(),
                    base.mutantOnlyWitness(),
                    base.attribution()));
        }
        return new ProspectiveReviewDossier(
                1,
                one.profile(),
                one.protocolArtifact(),
                one.manifestArtifact(),
                one.censusArtifact(),
                one.discoveryArtifact(),
                candidates);
    }

    static ProspectiveReviewLedger ledger(
            ProspectiveReviewDossier dossier, List<ProspectiveReviewLedger.Decision> decisions) {
        return new ProspectiveReviewLedger(
                1, ProspectiveProtocol.REVIEWER_MODE, dossier.protocolArtifact(), DOSSIER_ARTIFACT, decisions);
    }

    static ProspectiveReviewLedger.Decision accept(ProspectiveReviewDossier.CandidateKey key) throws Exception {
        String predicate = "ReviewedInvariant == x >= 0";
        return new ProspectiveReviewLedger.Decision(
                key,
                "ACCEPT",
                "ReviewedInvariant",
                predicate,
                sha256(predicate),
                ProspectiveReviewLedger.Criteria.allPassing(),
                evidence("Criterion-specific evidence."));
    }

    static ProspectiveReviewLedger.Decision reject(ProspectiveReviewDossier.CandidateKey key, String terminalDecision) {
        return new ProspectiveReviewLedger.Decision(
                key,
                terminalDecision,
                null,
                null,
                null,
                new ProspectiveReviewLedger.Criteria(true, true, true, true, true, false, true),
                evidence("The proposal duplicates the configured safety property."));
    }

    private static ProspectiveReviewLedger.Evidence evidence(String value) {
        return new ProspectiveReviewLedger.Evidence(value, value, value, value, value, value, value);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
