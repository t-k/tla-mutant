package dev.tlamutant.corpus;

import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.prospective.ProspectiveCensusReport;
import dev.tlamutant.prospective.ProspectiveReviewDossier;
import dev.tlamutant.prospective.ProspectiveReviewLedger;
import dev.tlamutant.prospective.ProspectiveReviewValidator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProspectiveInterventionValidator {
    private ProspectiveInterventionValidator() {}

    public static ValidatedCorpusManifest validate(
            CorpusManifest v10,
            CorpusManifest v11,
            ProspectiveReviewDossier dossier,
            ProspectiveReviewLedger ledger,
            ProspectiveReviewValidator.ValidatedReview review,
            ProspectiveCensusReport derivedCensus,
            ExecutionPolicy.Limits globalLimits) {
        Objects.requireNonNull(v10, "v10");
        Objects.requireNonNull(v11, "v11");
        Objects.requireNonNull(dossier, "dossier");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(derivedCensus, "derivedCensus");
        Objects.requireNonNull(globalLimits, "globalLimits");
        validateOuterIdentity(v10, v11, derivedCensus);
        if (review.eligible() != dossier.candidates().size()
                || review.reviewed() != ledger.decisions().size()
                || review.selectedInterventions().size() > 3) {
            throw new IllegalArgumentException("Prospective intervention review totals are inconsistent");
        }

        Map<ProspectiveReviewDossier.CandidateKey, ProspectiveReviewLedger.Decision> decisions = new HashMap<>();
        for (ProspectiveReviewLedger.Decision decision : ledger.decisions()) {
            if (decisions.put(decision.key(), decision) != null) {
                throw new IllegalArgumentException("Prospective intervention review keys must be unique");
            }
        }
        Set<ProspectiveReviewDossier.CandidateKey> dossierKeys = dossier.candidates().stream()
                .map(ProspectiveReviewDossier.Candidate::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ProspectiveReviewDossier.CandidateKey> selected = new HashSet<>(review.selectedInterventions());
        if (selected.size() != review.selectedInterventions().size() || !dossierKeys.containsAll(selected)) {
            throw new IllegalArgumentException("Prospective intervention selection is not in the dossier");
        }

        Map<String, ProspectiveReviewDossier.CandidateKey> selectedByEntry = new HashMap<>();
        for (ProspectiveReviewDossier.CandidateKey key : selected) {
            if (selectedByEntry.put(key.entryId(), key) != null) {
                throw new IllegalArgumentException("Prospective intervention permits at most one edit per entry");
            }
            ProspectiveReviewLedger.Decision decision = decisions.get(key);
            if (decision == null || !decision.terminalDecision().equals("ACCEPT")) {
                throw new IllegalArgumentException("Only accepted prospective decisions can authorize intervention");
            }
        }

        for (int index = 0; index < v10.entries().size(); index++) {
            CorpusManifest.Entry base = v10.entries().get(index);
            CorpusManifest.Entry derived = v11.entries().get(index);
            ManifestValidator.validateEntry(derived, globalLimits);
            requireCommonEntryIdentity(base, derived);
            ProspectiveReviewDossier.CandidateKey key = selectedByEntry.get(base.id());
            if (key == null) {
                if (!derived.adoptedTargetInvariants().isEmpty()
                        || !derived.derivedSourceEdits().isEmpty()) {
                    throw new IllegalArgumentException("Unselected prospective entry contains an intervention");
                }
                continue;
            }
            ProspectiveReviewLedger.Decision decision = decisions.get(key);
            validateSelectedEdit(base, derived, decision);
            requireCandidateInDerivedCensus(
                    key, derivedCensus.census().entries().get(index));
        }
        return new ValidatedCorpusManifest(v11, "prospective-v11");
    }

    private static void validateOuterIdentity(
            CorpusManifest v10, CorpusManifest v11, ProspectiveCensusReport derivedCensus) {
        if (v10.version() != 4
                || !"gcd-prospective-v10".equals(v10.profile())
                || v11.version() != 5
                || !"gcd-prospective-v11".equals(v11.profile())
                || !v10.repository().equals(v11.repository())
                || !v10.commit().equals(v11.commit())
                || !v10.license().equals(v11.license())
                || v10.entries().size() != v11.entries().size()
                || !v11.profile().equals(derivedCensus.census().profile())
                || !v11.repository().equals(derivedCensus.census().repository())
                || !v11.commit().equals(derivedCensus.census().commit())
                || v11.entries().size() != derivedCensus.census().entries().size()) {
            throw new IllegalArgumentException("Prospective intervention outer identity is invalid");
        }
    }

    private static void requireCommonEntryIdentity(CorpusManifest.Entry base, CorpusManifest.Entry derived) {
        if (!base.id().equals(derived.id())
                || !base.domain().equals(derived.domain())
                || !base.rootModule().equals(derived.rootModule())
                || !base.config().equals(derived.config())
                || !base.files().equals(derived.files())
                || !base.typeInvariants().equals(derived.typeInvariants())
                || !base.targetInvariants().equals(derived.targetInvariants())
                || !base.limits().equals(derived.limits())
                || !base.selection().equals(derived.selection())
                || !base.adoptedTargetInvariants().isEmpty()
                || !base.derivedSourceEdits().isEmpty()) {
            throw new IllegalArgumentException("Prospective intervention changed frozen entry metadata");
        }
    }

    private static void validateSelectedEdit(
            CorpusManifest.Entry base, CorpusManifest.Entry derived, ProspectiveReviewLedger.Decision decision) {
        if (derived.adoptedTargetInvariants().size() != 1
                || !derived.adoptedTargetInvariants().getFirst().equals(decision.operator())
                || derived.derivedSourceEdits().size() != 1) {
            throw new IllegalArgumentException("Prospective selected entry requires one reviewed oracle and edit");
        }
        CorpusManifest.DerivedSourceEdit edit = derived.derivedSourceEdits().getFirst();
        CorpusManifest.SourceFile root = base.files().stream()
                .filter(file -> file.path().equals(base.rootModule()))
                .findFirst()
                .orElseThrow();
        String expectedInsertion = "\n" + decision.predicate() + "\n\n";
        if (!edit.path().equals(base.rootModule())
                || !edit.definedOperator().equals(decision.operator())
                || !edit.baseSha256().equals(root.sha256())
                || edit.baseSizeBytes() != root.sizeBytes()
                || !edit.insertedText().equals(expectedInsertion)) {
            throw new IllegalArgumentException("Prospective source edit differs from the accepted review predicate");
        }
    }

    private static void requireCandidateInDerivedCensus(
            ProspectiveReviewDossier.CandidateKey key, CorpusCensusEntryResult censusEntry) {
        if (!censusEntry.id().equals(key.entryId())
                || !censusEntry.status().equals("COMPLETED")
                || censusEntry.report() == null
                || censusEntry.report().sites().stream()
                        .noneMatch(site -> site.candidateId().equals(key.candidateId()))) {
            throw new IllegalArgumentException("Selected prospective candidate is absent from the derived census");
        }
    }
}
