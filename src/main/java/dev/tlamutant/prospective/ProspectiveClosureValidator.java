package dev.tlamutant.prospective;

import dev.tlamutant.experiment.GcdTerminalOutcome;
import dev.tlamutant.experiment.KillAttribution;
import dev.tlamutant.experiment.MutationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProspectiveClosureValidator {
    private static final Set<String> ALLOWED_RELATIONS = Set.of("STATE_EXPANDING", "STATE_INCOMPARABLE");

    private ProspectiveClosureValidator() {}

    public static ProspectiveClosureAssessment evaluate(
            ProspectiveReviewDossier dossier,
            ProspectiveReviewLedger ledger,
            ProspectiveReviewValidator.ValidatedReview review,
            ProspectiveDiscoveryReport source,
            ProspectiveDiscoveryReport intervention) {
        Objects.requireNonNull(dossier, "dossier");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(intervention, "intervention");
        if (!review.isBoundTo(dossier, ledger)
                || review.selectedInterventions().isEmpty()
                || !dossier.profile().equals(source.profile())
                || !"gcd-prospective-v11".equals(intervention.profile())
                || !source.repository().equals(intervention.repository())
                || !source.commit().equals(intervention.commit())) {
            throw new IllegalArgumentException("Prospective closure inputs are not bound to one evaluation");
        }

        List<ProspectiveClosureAssessment.Outcome> outcomes = new ArrayList<>();
        int closed = 0;
        int rejectedByBaseline = 0;
        for (ProspectiveReviewDossier.CandidateKey key : review.selectedInterventions()) {
            ProspectiveReviewDossier.Candidate candidate = dossier.candidates().stream()
                    .filter(item -> item.key().equals(key))
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalArgumentException("Selected closure candidate is absent from dossier"));
            MutationResult sourceResult = result(source, key);
            MutationResult interventionResult = resultOrNull(intervention, key);
            ProspectiveClosureAssessment.Outcome outcome = classify(candidate, sourceResult, interventionResult);
            outcomes.add(outcome);
            if (outcome.status().equals("CLOSED")) {
                closed++;
            } else if (outcome.status().equals("REJECTED_BY_BASELINE")) {
                rejectedByBaseline++;
            }
        }
        int unresolved = outcomes.size() - closed - rejectedByBaseline;
        return new ProspectiveClosureAssessment(
                1,
                intervention.profile(),
                ProspectiveProtocol.REVIEWER_MODE,
                closed == outcomes.size() ? "VERIFIED" : "UNRESOLVED",
                outcomes.size(),
                closed,
                rejectedByBaseline,
                unresolved,
                outcomes);
    }

    private static ProspectiveClosureAssessment.Outcome classify(
            ProspectiveReviewDossier.Candidate candidate, MutationResult source, MutationResult intervention) {
        if (!ProspectiveDiscoveryReport.isEligible(source)
                || !candidate.executionStatus().equals(source.executionStatus())
                || !candidate.oracleOutcome().equals(source.oracleOutcome())
                || !candidate.stateRelation().equals(source.stateRelation())
                || !candidate.mutantOnlyWitness().equals(source.mutantOnlyWitness())) {
            throw new IllegalArgumentException("Prospective source candidate no longer matches the frozen dossier");
        }
        if (intervention == null) {
            return unresolved(candidate.key(), source, null, "Selected intervention result is missing");
        }
        KillAttribution attribution = intervention.attribution();
        if (attribution == null) {
            return unresolved(candidate.key(), source, intervention, "Selected intervention attribution is missing");
        }
        boolean typeEvidence = "COMPLETED".equals(attribution.typeOnlyExecutionStatus())
                && "PASSED".equals(attribution.typeOnlyOracleOutcome())
                && Objects.equals(attribution.typeOnlyStateCount(), source.mutantStateCount())
                && attribution.typeOnlyStateRelation() != null
                && ALLOWED_RELATIONS.contains(attribution.typeOnlyStateRelation())
                && attribution.typeOnlyStateRelation().equals(source.stateRelation())
                && source.mutantOnlyWitness() != null
                && !source.mutantOnlyWitness().isBlank()
                && source.mutantOnlyWitness().equals(attribution.typeOnlyMutantOnlyWitness());
        boolean legacyEvidence = candidate.legacyTargetInvariants().isEmpty()
                || "COMPLETED".equals(attribution.legacyExecutionStatus())
                        && "PASSED".equals(attribution.legacyOracleOutcome())
                        && source.stateRelation().equals(attribution.legacyStateRelation())
                        && source.mutantOnlyWitness().equals(attribution.legacyMutantOnlyWitness());
        boolean adoptedKill = intervention.executionStatus().equals("COMPLETED")
                && intervention.oracleOutcome().equals("KILLED_STATE_SAFETY")
                && intervention.verdict().equals("adopted-target-invariant-kill")
                && attribution.category().equals("adopted-target-invariant-kill")
                && intervention.baselineStateCount() == source.baselineStateCount();
        if (!typeEvidence) {
            return unresolved(
                    candidate.key(), source, intervention, "Type-only relation or witness does not match v10");
        }
        if (!legacyEvidence) {
            return unresolved(candidate.key(), source, intervention, "Legacy relation or witness does not match v10");
        }
        if (!adoptedKill) {
            return unresolved(candidate.key(), source, intervention, "Full adopted-oracle kill evidence is incomplete");
        }
        return outcome(candidate.key(), "CLOSED", "All preregistered closure conditions passed", source, intervention);
    }

    private static ProspectiveClosureAssessment.Outcome unresolved(
            ProspectiveReviewDossier.CandidateKey key,
            MutationResult source,
            MutationResult intervention,
            String reason) {
        return outcome(key, "UNRESOLVED", reason, source, intervention);
    }

    private static ProspectiveClosureAssessment.Outcome outcome(
            ProspectiveReviewDossier.CandidateKey key,
            String status,
            String reason,
            MutationResult source,
            MutationResult intervention) {
        KillAttribution attribution = intervention == null ? null : intervention.attribution();
        return new ProspectiveClosureAssessment.Outcome(
                key,
                status,
                reason,
                source.baselineStateCount(),
                source.mutantStateCount(),
                attribution == null ? null : attribution.typeOnlyStateCount(),
                source.stateRelation(),
                attribution == null ? null : attribution.typeOnlyStateRelation(),
                source.mutantOnlyWitness(),
                attribution == null ? null : attribution.typeOnlyMutantOnlyWitness(),
                intervention == null ? null : intervention.verdict());
    }

    private static MutationResult result(ProspectiveDiscoveryReport report, ProspectiveReviewDossier.CandidateKey key) {
        MutationResult result = resultOrNull(report, key);
        if (result == null) {
            throw new IllegalArgumentException("Prospective source result is missing");
        }
        return result;
    }

    private static MutationResult resultOrNull(
            ProspectiveDiscoveryReport report, ProspectiveReviewDossier.CandidateKey key) {
        return report.gcdOutcomes().stream()
                .filter(outcome -> outcome.entryId().equals(key.entryId()))
                .map(GcdTerminalOutcome::result)
                .filter(result -> result.id().equals(key.candidateId()))
                .findFirst()
                .orElse(null);
    }
}
