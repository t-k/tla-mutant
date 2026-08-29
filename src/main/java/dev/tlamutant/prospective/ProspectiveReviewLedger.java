package dev.tlamutant.prospective;

import dev.tlamutant.census.CensusInputIdentity;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record ProspectiveReviewLedger(
        int schemaVersion,
        String reviewerMode,
        CensusInputIdentity.Artifact protocolArtifact,
        CensusInputIdentity.Artifact dossierArtifact,
        List<Decision> decisions) {
    public ProspectiveReviewLedger {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Prospective review ledger schemaVersion must be 1");
        }
        if (!ProspectiveProtocol.REVIEWER_MODE.equals(reviewerMode)) {
            throw new IllegalArgumentException("Prospective reviewer mode differs from the approved delegation");
        }
        Objects.requireNonNull(protocolArtifact, "protocolArtifact");
        Objects.requireNonNull(dossierArtifact, "dossierArtifact");
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
    }

    public record Decision(
            ProspectiveReviewDossier.CandidateKey key,
            String terminalDecision,
            String operator,
            String predicate,
            String predicateSha256,
            Criteria criteria,
            Evidence evidence) {
        private static final Pattern OPERATOR = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        public Decision {
            Objects.requireNonNull(key, "key");
            if (!ProspectiveProtocol.REVIEW_DECISIONS.contains(terminalDecision)) {
                throw new IllegalArgumentException("Prospective review terminal decision is unknown");
            }
            Objects.requireNonNull(criteria, "criteria");
            Objects.requireNonNull(evidence, "evidence");
            if (operator != null && !OPERATOR.matcher(operator).matches()) {
                throw new IllegalArgumentException("Prospective reviewed operator is invalid");
            }
            if (predicate != null) {
                ProspectiveText.requireSafeMultiline(predicate, "predicate", 4096);
            }
            if (predicateSha256 != null && !SHA256.matcher(predicateSha256).matches()) {
                throw new IllegalArgumentException("Prospective predicate SHA-256 is invalid");
            }
        }
    }

    public record Criteria(
            boolean exactPredicate,
            boolean witnessFalsifies,
            boolean baselineHolds,
            boolean actionsPreserve,
            boolean nonvacuous,
            boolean nonredundant,
            boolean abstractionAligned) {
        public static Criteria allPassing() {
            return new Criteria(true, true, true, true, true, true, true);
        }

        public boolean allPass() {
            return exactPredicate
                    && witnessFalsifies
                    && baselineHolds
                    && actionsPreserve
                    && nonvacuous
                    && nonredundant
                    && abstractionAligned;
        }
    }

    public record Evidence(
            String exactPredicate,
            String witnessFalsifies,
            String baselineHolds,
            String actionsPreserve,
            String nonvacuous,
            String nonredundant,
            String abstractionAligned) {
        public Evidence {
            ProspectiveText.requireSafeMultiline(exactPredicate, "exactPredicate evidence", 2000);
            ProspectiveText.requireSafeMultiline(witnessFalsifies, "witnessFalsifies evidence", 2000);
            ProspectiveText.requireSafeMultiline(baselineHolds, "baselineHolds evidence", 2000);
            ProspectiveText.requireSafeMultiline(actionsPreserve, "actionsPreserve evidence", 2000);
            ProspectiveText.requireSafeMultiline(nonvacuous, "nonvacuous evidence", 2000);
            ProspectiveText.requireSafeMultiline(nonredundant, "nonredundant evidence", 2000);
            ProspectiveText.requireSafeMultiline(abstractionAligned, "abstractionAligned evidence", 2000);
        }
    }
}
