package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.CorpusCensusReport;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.ProspectiveInterventionValidator;
import dev.tlamutant.corpus.ValidatedCorpusManifest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveInterventionValidatorTest {
    private static final ExecutionPolicy.Limits GLOBAL =
            new ExecutionPolicy.Limits(120, 1024, 64, 128, 4096, 100, 2048, 2048, 10_000, 180);
    private static final CensusInputIdentity.Artifact DOSSIER_ARTIFACT =
            new CensusInputIdentity.Artifact("9".repeat(64), 90);

    @Test
    void bindsTheSelectedReviewPredicateToTheSameRootEntryAndCandidate() throws Exception {
        Fixture fixture = fixture();

        ValidatedCorpusManifest validated = ProspectiveInterventionValidator.validate(
                fixture.v10(),
                fixture.v11(),
                fixture.dossier(),
                fixture.ledger(),
                fixture.review(),
                fixture.derivedCensus(),
                GLOBAL);

        assertEquals("prospective-v11", validated.purpose());
        assertEquals(
                List.of("ReviewedInvariant"),
                validated.manifest().entries().getFirst().adoptedTargetInvariants());
    }

    @Test
    void rejectsChangedOperatorPredicateEntryOrNonRootEdit() throws Exception {
        Fixture fixture = fixture();
        CorpusManifest.Entry valid = fixture.v11().entries().getFirst();
        CorpusManifest.DerivedSourceEdit edit = valid.derivedSourceEdits().getFirst();
        CorpusManifest changedOperator = replaceV11Entry(
                fixture.v11(), withIntervention(valid, "DifferentOperator", edit.insertedText(), edit.path()));
        CorpusManifest changedPredicate = replaceV11Entry(
                fixture.v11(),
                withIntervention(valid, edit.definedOperator(), "\nReviewedInvariant == FALSE\n\n", edit.path()));
        CorpusManifest nonRoot = replaceV11Entry(
                fixture.v11(), withIntervention(valid, edit.definedOperator(), edit.insertedText(), "Spec0.cfg"));

        for (CorpusManifest invalid : List.of(changedOperator, changedPredicate, nonRoot)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ProspectiveInterventionValidator.validate(
                            fixture.v10(),
                            invalid,
                            fixture.dossier(),
                            fixture.ledger(),
                            fixture.review(),
                            fixture.derivedCensus(),
                            GLOBAL));
        }
    }

    @Test
    void rejectsMissingSelectedCandidateInDerivedCensusAndAnyEditWithZeroAccepts() throws Exception {
        Fixture fixture = fixture();
        List<CorpusCensusEntryResult> censusEntries =
                new ArrayList<>(fixture.derivedCensus().census().entries());
        CorpusCensusEntryResult first = censusEntries.getFirst();
        censusEntries.set(
                0,
                new CorpusCensusEntryResult(
                        first.id(),
                        first.domain(),
                        first.selection(),
                        "COMPLETED",
                        null,
                        new dev.tlamutant.census.GcdCensusReport(
                                1, first.report().module(), first.report().config(), List.of())));
        ProspectiveCensusReport missingCandidate = new ProspectiveCensusReport(
                1,
                fixture.derivedCensus().receipt(),
                new CorpusCensusReport(
                        3,
                        fixture.derivedCensus().census().profile(),
                        fixture.derivedCensus().census().repository(),
                        fixture.derivedCensus().census().commit(),
                        fixture.derivedCensus().census().inputs(),
                        censusEntries));
        ProspectiveReviewLedger rejectedLedger = ProspectiveReviewValidatorTest.ledger(
                fixture.dossier(),
                List.of(ProspectiveReviewValidatorTest.reject(
                        fixture.dossier().candidates().getFirst().key(), "REDUNDANT")));
        ProspectiveReviewValidator.ValidatedReview rejectedReview =
                ProspectiveReviewValidator.validate(fixture.dossier(), DOSSIER_ARTIFACT, rejectedLedger, 3);

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveInterventionValidator.validate(
                        fixture.v10(),
                        fixture.v11(),
                        fixture.dossier(),
                        fixture.ledger(),
                        fixture.review(),
                        missingCandidate,
                        GLOBAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveInterventionValidator.validate(
                        fixture.v10(),
                        fixture.v11(),
                        fixture.dossier(),
                        rejectedLedger,
                        rejectedReview,
                        fixture.derivedCensus(),
                        GLOBAL));
    }

    private static Fixture fixture() throws Exception {
        ProspectiveDiscoveryReportTest.Fixture discovery = ProspectiveDiscoveryReportTest.fixture();
        CorpusManifest v10 = discovery.manifest();
        ProspectiveReviewDossier dossier = ProspectiveReviewValidatorTest.dossier(1);
        ProspectiveReviewLedger.Decision decision = ProspectiveReviewValidatorTest.accept(
                dossier.candidates().getFirst().key());
        ProspectiveReviewLedger ledger = ProspectiveReviewValidatorTest.ledger(dossier, List.of(decision));
        ProspectiveReviewValidator.ValidatedReview review =
                ProspectiveReviewValidator.validate(dossier, DOSSIER_ARTIFACT, ledger, 3);
        List<CorpusManifest.Entry> entries = new ArrayList<>(v10.entries());
        entries.set(0, intervention(entries.getFirst(), decision));
        CorpusManifest v11 =
                new CorpusManifest(5, "gcd-prospective-v11", v10.repository(), v10.commit(), v10.license(), entries);
        CorpusCensusReport baseCensus = discovery.census().census();
        ProspectiveCensusReport derivedCensus = new ProspectiveCensusReport(
                1,
                new CensusInputIdentity.Artifact("8".repeat(64), 80),
                new CorpusCensusReport(
                        3, v11.profile(), v11.repository(), v11.commit(), baseCensus.inputs(), baseCensus.entries()));
        return new Fixture(v10, v11, dossier, ledger, review, derivedCensus);
    }

    private static CorpusManifest.Entry intervention(
            CorpusManifest.Entry entry, ProspectiveReviewLedger.Decision decision) {
        CorpusManifest.SourceFile root = entry.files().getFirst();
        String inserted = "\n" + decision.predicate() + "\n\n";
        CorpusManifest.DerivedSourceEdit edit = new CorpusManifest.DerivedSourceEdit(
                entry.rootModule(),
                decision.operator(),
                root.sha256(),
                root.sizeBytes(),
                root.sizeBytes(),
                inserted,
                "7".repeat(64),
                root.sizeBytes() + inserted.getBytes(StandardCharsets.UTF_8).length);
        return new CorpusManifest.Entry(
                entry.id(),
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                entry.files(),
                entry.typeInvariants(),
                entry.targetInvariants(),
                List.of(decision.operator()),
                List.of(edit),
                entry.limits(),
                entry.selection());
    }

    private static CorpusManifest.Entry withIntervention(
            CorpusManifest.Entry entry, String operator, String inserted, String path) {
        CorpusManifest.SourceFile root = entry.files().getFirst();
        CorpusManifest.DerivedSourceEdit edit = new CorpusManifest.DerivedSourceEdit(
                path,
                operator,
                root.sha256(),
                root.sizeBytes(),
                root.sizeBytes(),
                inserted,
                "6".repeat(64),
                root.sizeBytes() + inserted.getBytes(StandardCharsets.UTF_8).length);
        return new CorpusManifest.Entry(
                entry.id(),
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                entry.files(),
                entry.typeInvariants(),
                entry.targetInvariants(),
                List.of(operator),
                List.of(edit),
                entry.limits(),
                entry.selection());
    }

    private static CorpusManifest replaceV11Entry(CorpusManifest manifest, CorpusManifest.Entry replacement) {
        List<CorpusManifest.Entry> entries = new ArrayList<>(manifest.entries());
        entries.set(0, replacement);
        return new CorpusManifest(
                manifest.version(),
                manifest.profile(),
                manifest.repository(),
                manifest.commit(),
                manifest.license(),
                entries);
    }

    private record Fixture(
            CorpusManifest v10,
            CorpusManifest v11,
            ProspectiveReviewDossier dossier,
            ProspectiveReviewLedger ledger,
            ProspectiveReviewValidator.ValidatedReview review,
            ProspectiveCensusReport derivedCensus) {}
}
