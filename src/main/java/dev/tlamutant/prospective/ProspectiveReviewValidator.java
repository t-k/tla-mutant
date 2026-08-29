package dev.tlamutant.prospective;

import dev.tlamutant.census.CensusInputIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProspectiveReviewValidator {
    private ProspectiveReviewValidator() {}

    public static ValidatedReview validate(
            ProspectiveReviewDossier dossier,
            CensusInputIdentity.Artifact expectedDossierArtifact,
            ProspectiveReviewLedger ledger,
            int interventionCap) {
        Objects.requireNonNull(dossier, "dossier");
        Objects.requireNonNull(expectedDossierArtifact, "expectedDossierArtifact");
        Objects.requireNonNull(ledger, "ledger");
        if (interventionCap < 0 || interventionCap > 3) {
            throw new IllegalArgumentException("Prospective intervention cap must be between zero and three");
        }
        if (!ledger.protocolArtifact().equals(dossier.protocolArtifact())
                || !ledger.dossierArtifact().equals(expectedDossierArtifact)) {
            throw new IllegalArgumentException("Prospective review ledger identity differs from its dossier");
        }

        Set<ProspectiveReviewDossier.CandidateKey> expectedKeys = dossier.candidates().stream()
                .map(ProspectiveReviewDossier.Candidate::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<ProspectiveReviewDossier.CandidateKey, ProspectiveReviewLedger.Decision> decisions = new HashMap<>();
        for (ProspectiveReviewLedger.Decision decision : ledger.decisions()) {
            if (decisions.put(decision.key(), decision) != null) {
                throw new IllegalArgumentException("Prospective review candidate decisions must be unique");
            }
            validateDecision(decision);
        }
        if (!decisions.keySet().equals(expectedKeys)) {
            throw new IllegalArgumentException("Prospective review decisions must exactly cover the dossier");
        }

        List<ProspectiveReviewDossier.CandidateKey> accepted = decisions.values().stream()
                .filter(decision -> decision.terminalDecision().equals("ACCEPT"))
                .map(ProspectiveReviewLedger.Decision::key)
                .sorted()
                .toList();
        List<ProspectiveReviewDossier.CandidateKey> selected =
                accepted.subList(0, Math.min(interventionCap, accepted.size()));
        Map<String, Integer> terminalCounts = new LinkedHashMap<>();
        for (String terminal : ProspectiveProtocol.REVIEW_DECISIONS) {
            terminalCounts.put(terminal, (int) decisions.values().stream()
                    .filter(decision -> decision.terminalDecision().equals(terminal))
                    .count());
        }
        return new ValidatedReview(
                dossier.candidates().size(),
                decisions.size(),
                accepted.size(),
                accepted.size() - selected.size(),
                selected,
                terminalCounts);
    }

    private static void validateDecision(ProspectiveReviewLedger.Decision decision) {
        boolean accepted = decision.terminalDecision().equals("ACCEPT");
        if (accepted) {
            if (decision.operator() == null
                    || decision.predicate() == null
                    || decision.predicateSha256() == null
                    || !decision.criteria().allPass()
                    || !decision.predicateSha256().equals(sha256(decision.predicate()))) {
                throw new IllegalArgumentException("Prospective ACCEPT requires the exact predicate and seven passes");
            }
        } else if (decision.operator() != null
                || decision.predicate() != null
                || decision.predicateSha256() != null
                || decision.criteria().allPass()) {
            throw new IllegalArgumentException("Non-accepted prospective decisions cannot authorize a predicate");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record ValidatedReview(
            int eligible,
            int reviewed,
            int accepted,
            int acceptedOverflow,
            List<ProspectiveReviewDossier.CandidateKey> selectedInterventions,
            Map<String, Integer> terminalCounts) {
        public ValidatedReview {
            selectedInterventions = List.copyOf(Objects.requireNonNull(selectedInterventions, "selectedInterventions"));
            terminalCounts = Map.copyOf(Objects.requireNonNull(terminalCounts, "terminalCounts"));
            if (eligible < 0
                    || reviewed != eligible
                    || accepted < 0
                    || accepted > reviewed
                    || acceptedOverflow != accepted - selectedInterventions.size()
                    || terminalCounts.values().stream()
                                    .mapToInt(Integer::intValue)
                                    .sum()
                            != reviewed) {
                throw new IllegalArgumentException("Prospective review totals do not conserve decisions");
            }
        }
    }
}
