package dev.tlamutant.prospective;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.security.SafeRelativePath;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record ProspectiveProtocol(
        int schemaVersion,
        String profile,
        String repository,
        String commit,
        int standardRiskEntries,
        int highRiskEntries,
        int interventionCap,
        HistoricalSet historicalSet,
        Eligibility eligibility,
        ReviewRubric rubric,
        List<Entry> entries) {
    public static final String STANDARD_RISK = "standard-risk";
    public static final String HIGH_RISK = "high-risk-no-replacement";
    public static final String REVIEWER_MODE = "user-delegated-agent";
    public static final List<String> REVIEW_CRITERIA = List.of(
            "exact-predicate",
            "witness-falsifies-mutant",
            "baseline-holds",
            "actions-preserve",
            "nonvacuous",
            "nonredundant",
            "abstraction-aligned");
    public static final List<String> REVIEW_DECISIONS = List.of(
            "ACCEPT",
            "ALLOWED_STATE",
            "REDUNDANT",
            "NON_INDUCTIVE",
            "CONFIGURATION_FIX",
            "DOMAIN_OWNER_REQUIRED",
            "STRENGTHENING_REQUIRED");
    private static final Pattern PROFILE = Pattern.compile("gcd-prospective-v[0-9]+");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    public ProspectiveProtocol {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Prospective protocol schemaVersion must be 1");
        }
        if (profile == null || !PROFILE.matcher(profile).matches()) {
            throw new IllegalArgumentException("Prospective protocol profile is invalid");
        }
        ProspectiveText.requireSafeLine(repository, "repository", 500);
        if (!repository.startsWith("https://")) {
            throw new IllegalArgumentException("Prospective repository must use HTTPS");
        }
        if (commit == null || !COMMIT.matcher(commit).matches()) {
            throw new IllegalArgumentException("Prospective commit must be a lowercase Git SHA-1");
        }
        Objects.requireNonNull(historicalSet, "historicalSet");
        Objects.requireNonNull(eligibility, "eligibility");
        Objects.requireNonNull(rubric, "rubric");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.isEmpty() || entries.size() > 32) {
            throw new IllegalArgumentException("Prospective protocol must contain 1 to 32 entries");
        }
        if (standardRiskEntries < 0 || highRiskEntries < 0 || standardRiskEntries + highRiskEntries != entries.size()) {
            throw new IllegalArgumentException("Prospective risk counts do not match entries");
        }
        if (interventionCap < 0 || interventionCap > entries.size()) {
            throw new IllegalArgumentException("Prospective intervention cap is out of bounds");
        }
        Set<String> ids = new HashSet<>();
        long standard = 0;
        long high = 0;
        for (Entry entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Prospective entry IDs must be unique");
            }
            if (entry.riskClass().equals(STANDARD_RISK)) {
                standard++;
            } else if (entry.riskClass().equals(HIGH_RISK)) {
                high++;
            } else {
                throw new IllegalArgumentException("Prospective risk class is unknown");
            }
        }
        if (standard != standardRiskEntries || high != highRiskEntries) {
            throw new IllegalArgumentException("Prospective declared risk counts do not match entry risk classes");
        }
    }

    public static Eligibility requiredEligibility() {
        return new Eligibility(List.of("COMPLETED"), List.of("STATE_EXPANDING", "STATE_INCOMPARABLE"), true);
    }

    public static ReviewRubric requiredReviewRubric() {
        return new ReviewRubric(REVIEWER_MODE, REVIEW_CRITERIA, REVIEW_DECISIONS);
    }

    public record Entry(
            String id,
            String domain,
            String riskClass,
            String familyCluster,
            String rootModule,
            String config,
            List<String> repositoryPaths,
            List<String> typeInvariants,
            List<String> legacyTargetInvariants,
            ExecutionPolicy.Limits limits,
            String rationale) {
        private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
        private static final Pattern OPERATOR = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");

        public Entry {
            requireIdentifier(id, "id");
            requireIdentifier(domain, "domain");
            requireIdentifier(familyCluster, "familyCluster");
            if (!Set.of(STANDARD_RISK, HIGH_RISK).contains(riskClass)) {
                throw new IllegalArgumentException("Prospective entry risk class is unknown");
            }
            SafeRelativePath rootPath = SafeRelativePath.parse(rootModule);
            SafeRelativePath configPath = SafeRelativePath.parse(config);
            if (!rootPath.value().endsWith(".tla") || !configPath.value().endsWith(".cfg")) {
                throw new IllegalArgumentException("Prospective root and config extensions are invalid");
            }
            repositoryPaths = copyPaths(repositoryPaths);
            typeInvariants = copyOperators(typeInvariants, "typeInvariants");
            legacyTargetInvariants = copyOperators(legacyTargetInvariants, "legacyTargetInvariants");
            if (typeInvariants.isEmpty()) {
                throw new IllegalArgumentException("Prospective entry requires a type invariant");
            }
            Set<String> allOracles = new HashSet<>(typeInvariants);
            if (!allOracles.addAll(legacyTargetInvariants)) {
                throw new IllegalArgumentException("Prospective oracle names must be unique");
            }
            Objects.requireNonNull(limits, "limits");
            ProspectiveText.requireSafeLine(rationale, "rationale", 500);
        }

        private static List<String> copyPaths(List<String> values) {
            List<String> copy = List.copyOf(Objects.requireNonNull(values, "repositoryPaths"));
            if (copy.size() < 2 || copy.size() > 64 || new HashSet<>(copy).size() != copy.size()) {
                throw new IllegalArgumentException("Prospective repository paths must be unique and bounded");
            }
            copy.forEach(SafeRelativePath::parse);
            return copy;
        }

        private static List<String> copyOperators(List<String> values, String field) {
            List<String> copy = List.copyOf(Objects.requireNonNull(values, field));
            if (copy.size() > 64 || new HashSet<>(copy).size() != copy.size()) {
                throw new IllegalArgumentException(field + " must be unique and bounded");
            }
            if (copy.stream()
                    .anyMatch(value -> value == null || !OPERATOR.matcher(value).matches())) {
                throw new IllegalArgumentException(field + " contains an invalid operator");
            }
            return copy;
        }

        private static void requireIdentifier(String value, String field) {
            if (value == null || !IDENTIFIER.matcher(value).matches()) {
                throw new IllegalArgumentException(field + " must be a safe bounded identifier");
            }
        }
    }

    public record HistoricalSet(int rootPathCount, String rootPathSetSha256, int sourceCount, String sourceSetSha256) {
        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        public HistoricalSet {
            if (rootPathCount <= 0 || sourceCount <= 0 || sourceCount < rootPathCount) {
                throw new IllegalArgumentException("Historical source counts are invalid");
            }
            if (rootPathSetSha256 == null
                    || !SHA256.matcher(rootPathSetSha256).matches()
                    || sourceSetSha256 == null
                    || !SHA256.matcher(sourceSetSha256).matches()) {
                throw new IllegalArgumentException("Historical source set digests are invalid");
            }
        }
    }

    public record Eligibility(
            List<String> requiredExecutionStatuses, List<String> allowedStateRelations, boolean requireWitness) {
        public Eligibility {
            requiredExecutionStatuses =
                    List.copyOf(Objects.requireNonNull(requiredExecutionStatuses, "requiredExecutionStatuses"));
            allowedStateRelations = List.copyOf(Objects.requireNonNull(allowedStateRelations, "allowedStateRelations"));
            if (!requiredExecutionStatuses.equals(List.of("COMPLETED"))
                    || !allowedStateRelations.equals(List.of("STATE_EXPANDING", "STATE_INCOMPARABLE"))
                    || !requireWitness) {
                throw new IllegalArgumentException("Prospective eligibility rule differs from the approved rule");
            }
        }
    }

    public record ReviewRubric(String reviewerMode, List<String> criteria, List<String> terminalDecisions) {
        public ReviewRubric {
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            terminalDecisions = List.copyOf(Objects.requireNonNull(terminalDecisions, "terminalDecisions"));
            if (!REVIEWER_MODE.equals(reviewerMode)
                    || !criteria.equals(REVIEW_CRITERIA)
                    || !terminalDecisions.equals(REVIEW_DECISIONS)) {
                throw new IllegalArgumentException("Prospective review rubric differs from the approved rubric");
            }
        }
    }
}
