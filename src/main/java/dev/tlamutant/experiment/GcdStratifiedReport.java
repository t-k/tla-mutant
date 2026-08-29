package dev.tlamutant.experiment;

import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.CorpusCensusReport;
import dev.tlamutant.census.GcdCensusSite;
import dev.tlamutant.corpus.CorpusManifest;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record GcdStratifiedReport(
        int schemaVersion,
        String profile,
        String repository,
        String commit,
        String censusSha256,
        long censusSizeBytes,
        String automatedGateStatus,
        String humanReviewStatus,
        int specificationCount,
        int censusSiteCount,
        int brlGenerated,
        int brlEvaluated,
        int uoiGenerated,
        int uoiEvaluated,
        List<GcdOutcomeSummary> summaries,
        List<GcdTerminalOutcome> gcdOutcomes,
        CorpusCensusReport census,
        CorpusExperimentReport experiment) {
    public GcdStratifiedReport {
        if ((schemaVersion != 1 && schemaVersion != 2 && schemaVersion != 3)
                || censusSha256 == null
                || !censusSha256.matches("[0-9a-f]{64}")
                || censusSizeBytes <= 0
                || specificationCount < 0
                || censusSiteCount < 0
                || brlGenerated < 0
                || brlEvaluated < 0
                || uoiGenerated < 0
                || uoiEvaluated < 0) {
            throw new IllegalArgumentException("Invalid GCD-stratified report identity or totals");
        }
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(automatedGateStatus, "automatedGateStatus");
        Objects.requireNonNull(humanReviewStatus, "humanReviewStatus");
        Objects.requireNonNull(census, "census");
        Objects.requireNonNull(experiment, "experiment");
        summaries = List.copyOf(summaries);
        if (gcdOutcomes == null) {
            if (schemaVersion == 3) {
                throw new IllegalArgumentException("Schema 3 requires explicit GCD terminal outcomes");
            }
            gcdOutcomes = List.of();
        } else {
            gcdOutcomes = List.copyOf(gcdOutcomes);
        }
        if (schemaVersion == 3 && gcdOutcomes.size() != censusSiteCount) {
            throw new IllegalArgumentException("Every census GCD site requires one terminal outcome");
        }
    }

    public static GcdStratifiedReport create(
            CorpusManifest manifest,
            CorpusCensusReport census,
            String censusSha256,
            long censusSizeBytes,
            CorpusExperimentReport experiment) {
        requireOuterIdentity(manifest, census, experiment);
        Map<GroupKey, SummaryBuilder> builders = new HashMap<>();
        Set<String> globalCensusIds = new HashSet<>();
        Set<String> candidateSpecifications = new HashSet<>();
        Set<String> candidateDomains = new HashSet<>();
        Set<String> candidateClusters = new HashSet<>();
        int brlGenerated = 0;
        int brlEvaluated = 0;
        int uoiGenerated = 0;
        int uoiEvaluated = 0;
        List<GcdTerminalOutcome> gcdOutcomes = new java.util.ArrayList<>();

        for (int index = 0; index < manifest.entries().size(); index++) {
            CorpusManifest.Entry expected = manifest.entries().get(index);
            CorpusCensusEntryResult censusEntry = census.entries().get(index);
            CorpusEntryResult experimentEntry = experiment.entries().get(index);
            requireEntryIdentity(expected, censusEntry, experimentEntry);
            List<GroupKey> groups = groups(expected);
            groups.forEach(group -> builders.computeIfAbsent(group, ignored -> new SummaryBuilder()));

            Map<String, MutationResult> gcdResults = new LinkedHashMap<>();
            if (experimentEntry.report() != null) {
                for (MutationResult result : experimentEntry.report().results()) {
                    if (result.family().equals("GCD") && gcdResults.put(result.id(), result) != null) {
                        throw new IllegalArgumentException("Experiment GCD candidate IDs must be unique per entry");
                    }
                    if (result.family().equals("BRL")) {
                        brlGenerated++;
                        brlEvaluated += result.executionStatus().equals("COMPLETED") ? 1 : 0;
                    } else if (result.family().equals("UOI")) {
                        uoiGenerated++;
                        uoiEvaluated += result.executionStatus().equals("COMPLETED") ? 1 : 0;
                    }
                }
            }

            Set<String> censusIds = new HashSet<>();
            for (GcdCensusSite site : censusEntry.report().sites()) {
                if (!censusIds.add(site.candidateId()) || !globalCensusIds.add(site.candidateId())) {
                    throw new IllegalArgumentException("Census GCD candidate IDs must be globally unique");
                }
            }
            if (experimentEntry.report() != null && !gcdResults.keySet().equals(censusIds)) {
                throw new IllegalArgumentException("Completed experiment GCD IDs must equal the frozen census IDs");
            }

            Set<Bucket> specificationBuckets = EnumSet.noneOf(Bucket.class);
            boolean specificationHasWitness = false;
            for (GcdCensusSite site : censusEntry.report().sites()) {
                MutationResult result = gcdResults.get(site.candidateId());
                MutationResult terminal = result == null ? unresolved(experimentEntry, site) : result;
                gcdOutcomes.add(new GcdTerminalOutcome(expected.id(), terminal));
                Bucket bucket = result == null ? Bucket.UNRESOLVED : bucket(result);
                boolean witnessBearing = result != null && witnessBearing(result);
                specificationBuckets.add(bucket);
                specificationHasWitness |= witnessBearing;
                for (GroupKey group : groups) {
                    builders.get(group).add(bucket, witnessBearing);
                }
                if (witnessBearing) {
                    candidateSpecifications.add(expected.id());
                    candidateDomains.add(expected.domain());
                    candidateClusters.add(expected.selection().familyCluster());
                }
            }
            for (GroupKey group : groups) {
                builders.get(group).addSpecification(specificationBuckets, specificationHasWitness);
            }
        }

        List<GcdOutcomeSummary> summaries = builders.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().build(entry.getKey()))
                .toList();
        int censusSiteCount = summaries.stream()
                .filter(summary -> summary.grouping().equals("overall"))
                .findFirst()
                .orElseThrow()
                .totalSites();
        String automatedGateStatus =
                candidateSpecifications.size() >= 2 && candidateDomains.size() >= 2 && candidateClusters.size() >= 2
                        ? "human-review-eligible"
                        : "insufficient-witness-bearing-gcd-candidates";
        return new GcdStratifiedReport(
                3,
                manifest.profile(),
                manifest.repository(),
                manifest.commit(),
                censusSha256,
                censusSizeBytes,
                automatedGateStatus,
                "pending",
                manifest.entries().size(),
                censusSiteCount,
                brlGenerated,
                brlEvaluated,
                uoiGenerated,
                uoiEvaluated,
                summaries,
                gcdOutcomes,
                census,
                experiment);
    }

    private static MutationResult unresolved(CorpusEntryResult entry, GcdCensusSite site) {
        return new MutationResult(
                site.candidateId(),
                "GCD",
                site.definition(),
                site.guard(),
                "TRUE",
                "BASELINE_UNRESOLVED",
                "NOT_EVALUATED",
                "STATE_UNRESOLVED",
                "unresolved",
                "Baseline entry status: " + entry.status(),
                0,
                0,
                null);
    }

    private static void requireOuterIdentity(
            CorpusManifest manifest, CorpusCensusReport census, CorpusExperimentReport experiment) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(census, "census");
        Objects.requireNonNull(experiment, "experiment");
        if (!Objects.equals(manifest.profile(), census.profile())
                || !manifest.repository().equals(census.repository())
                || !manifest.repository().equals(experiment.repository())
                || !manifest.commit().equals(census.commit())
                || !manifest.commit().equals(experiment.commit())
                || manifest.entries().size() != census.entries().size()
                || manifest.entries().size() != experiment.entries().size()) {
            throw new IllegalArgumentException("Manifest, census, and experiment identities must match exactly");
        }
    }

    private static void requireEntryIdentity(
            CorpusManifest.Entry expected, CorpusCensusEntryResult censusEntry, CorpusEntryResult experimentEntry) {
        if (!expected.id().equals(censusEntry.id())
                || !expected.id().equals(experimentEntry.id())
                || !expected.domain().equals(censusEntry.domain())
                || !expected.domain().equals(experimentEntry.domain())
                || !expected.selection().equals(censusEntry.selection())
                || !censusEntry.status().equals("COMPLETED")
                || !expected.rootModule().equals(censusEntry.report().module())
                || !expected.config().equals(censusEntry.report().config())
                || (experimentEntry.report() != null
                        && (!expected.rootModule()
                                        .equals(experimentEntry.report().module())
                                || !expected.config()
                                        .equals(experimentEntry.report().config())))) {
            throw new IllegalArgumentException("Manifest, census, and experiment entry identities must match exactly");
        }
    }

    private static List<GroupKey> groups(CorpusManifest.Entry entry) {
        return List.of(
                new GroupKey("overall", "all"),
                new GroupKey("cohort", entry.selection().cohort()),
                new GroupKey("stratum", entry.selection().gcdStratum()),
                new GroupKey("family-cluster", entry.selection().familyCluster()),
                new GroupKey("domain", entry.domain()));
    }

    private static Bucket bucket(MutationResult result) {
        if (result.verdict().equals("state-invariant-kill-unattributed")) {
            return Bucket.KILLED_UNATTRIBUTED;
        }
        if (result.verdict().equals("invalid") || result.executionStatus().startsWith("INVALID_")) {
            return Bucket.INVALID;
        }
        return switch (result.stateRelation()) {
            case "STATE_EQUAL" -> Bucket.STATE_EQUAL;
            case "STATE_EXPANDING" -> Bucket.STATE_EXPANDING;
            case "STATE_INCOMPARABLE" -> Bucket.STATE_INCOMPARABLE;
            case "STATE_RESTRICTING" -> Bucket.STATE_RESTRICTING;
            default -> Bucket.UNRESOLVED;
        };
    }

    private static boolean witnessBearing(MutationResult result) {
        return result.verdict().equals("state-invariant-gap-candidate")
                && (result.stateRelation().equals("STATE_EXPANDING")
                        || result.stateRelation().equals("STATE_INCOMPARABLE"))
                && result.mutantOnlyWitness() != null
                && !result.mutantOnlyWitness().isBlank();
    }

    private enum Bucket {
        KILLED_UNATTRIBUTED,
        STATE_EQUAL,
        STATE_EXPANDING,
        STATE_INCOMPARABLE,
        STATE_RESTRICTING,
        INVALID,
        UNRESOLVED
    }

    private record GroupKey(String grouping, String value) implements Comparable<GroupKey> {
        @Override
        public int compareTo(GroupKey other) {
            if (grouping.equals("overall") && !other.grouping.equals("overall")) {
                return -1;
            }
            if (!grouping.equals("overall") && other.grouping.equals("overall")) {
                return 1;
            }
            return Comparator.comparing(GroupKey::grouping)
                    .thenComparing(GroupKey::value)
                    .compare(this, other);
        }
    }

    private static final class SummaryBuilder {
        private int specifications;
        private int killedUnattributed;
        private int stateEqual;
        private int stateExpanding;
        private int stateIncomparable;
        private int stateRestricting;
        private int invalid;
        private int unresolved;
        private int witnessBearingCandidates;
        private int specificationsKilledUnattributed;
        private int specificationsStateEqual;
        private int specificationsStateExpanding;
        private int specificationsStateIncomparable;
        private int specificationsStateRestricting;
        private int specificationsInvalid;
        private int specificationsUnresolved;
        private int specificationsWithWitness;

        void addSpecification(Set<Bucket> buckets, boolean witnessBearing) {
            specifications++;
            specificationsKilledUnattributed += buckets.contains(Bucket.KILLED_UNATTRIBUTED) ? 1 : 0;
            specificationsStateEqual += buckets.contains(Bucket.STATE_EQUAL) ? 1 : 0;
            specificationsStateExpanding += buckets.contains(Bucket.STATE_EXPANDING) ? 1 : 0;
            specificationsStateIncomparable += buckets.contains(Bucket.STATE_INCOMPARABLE) ? 1 : 0;
            specificationsStateRestricting += buckets.contains(Bucket.STATE_RESTRICTING) ? 1 : 0;
            specificationsInvalid += buckets.contains(Bucket.INVALID) ? 1 : 0;
            specificationsUnresolved += buckets.contains(Bucket.UNRESOLVED) ? 1 : 0;
            specificationsWithWitness += witnessBearing ? 1 : 0;
        }

        void add(Bucket bucket, boolean witnessBearing) {
            switch (bucket) {
                case KILLED_UNATTRIBUTED -> killedUnattributed++;
                case STATE_EQUAL -> stateEqual++;
                case STATE_EXPANDING -> stateExpanding++;
                case STATE_INCOMPARABLE -> stateIncomparable++;
                case STATE_RESTRICTING -> stateRestricting++;
                case INVALID -> invalid++;
                case UNRESOLVED -> unresolved++;
            }
            witnessBearingCandidates += witnessBearing ? 1 : 0;
        }

        GcdOutcomeSummary build(GroupKey group) {
            int totalSites = killedUnattributed
                    + stateEqual
                    + stateExpanding
                    + stateIncomparable
                    + stateRestricting
                    + invalid
                    + unresolved;
            return new GcdOutcomeSummary(
                    group.grouping(),
                    group.value(),
                    specifications,
                    totalSites,
                    killedUnattributed,
                    stateEqual,
                    stateExpanding,
                    stateIncomparable,
                    stateRestricting,
                    invalid,
                    unresolved,
                    witnessBearingCandidates,
                    specificationsKilledUnattributed,
                    specificationsStateEqual,
                    specificationsStateExpanding,
                    specificationsStateIncomparable,
                    specificationsStateRestricting,
                    specificationsInvalid,
                    specificationsUnresolved,
                    specificationsWithWitness);
        }
    }
}
