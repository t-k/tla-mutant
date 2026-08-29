package dev.tlamutant.prospective;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.GcdCensusSite;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.experiment.CorpusEntryResult;
import dev.tlamutant.experiment.CorpusExperimentReport;
import dev.tlamutant.experiment.GcdTerminalOutcome;
import dev.tlamutant.experiment.MutationResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ProspectiveDiscoveryReport(
        int schemaVersion,
        String profile,
        String repository,
        String commit,
        CensusInputIdentity.Artifact discoveryReceipt,
        CensusInputIdentity.Artifact experimentReceipt,
        CensusInputIdentity.Artifact censusArtifact,
        List<SpecificationOutcome> specifications,
        List<GcdTerminalOutcome> gcdOutcomes,
        DiscoverySummary summary,
        ProspectiveCensusReport census,
        CorpusExperimentReport experiment) {
    public ProspectiveDiscoveryReport {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Prospective discovery schemaVersion must be 1");
        }
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(discoveryReceipt, "discoveryReceipt");
        Objects.requireNonNull(experimentReceipt, "experimentReceipt");
        Objects.requireNonNull(censusArtifact, "censusArtifact");
        specifications = List.copyOf(Objects.requireNonNull(specifications, "specifications"));
        gcdOutcomes = List.copyOf(Objects.requireNonNull(gcdOutcomes, "gcdOutcomes"));
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(census, "census");
        Objects.requireNonNull(experiment, "experiment");
        if (summary.specifications() != specifications.size() || summary.censusSites() != gcdOutcomes.size()) {
            throw new IllegalArgumentException("Prospective discovery summary does not conserve outcomes");
        }
    }

    public static ProspectiveDiscoveryReport create(
            CorpusManifest manifest,
            ProspectiveCensusReport census,
            CensusInputIdentity.Artifact censusArtifact,
            CensusInputIdentity.Artifact experimentReceipt,
            CorpusExperimentReport experiment) {
        requireOuterIdentity(manifest, census, experiment);
        List<SpecificationOutcome> specifications = new ArrayList<>();
        List<GcdTerminalOutcome> outcomes = new ArrayList<>();
        int censusCompleted = 0;
        int censusCensored = 0;
        int experimentCompleted = 0;
        int experimentCensored = 0;
        int completedGcd = 0;
        int unresolvedGcd = 0;
        int eligible = 0;

        for (int index = 0; index < manifest.entries().size(); index++) {
            CorpusManifest.Entry expected = manifest.entries().get(index);
            CorpusCensusEntryResult censusEntry = census.census().entries().get(index);
            CorpusEntryResult experimentEntry = experiment.entries().get(index);
            requireEntryIdentity(expected, censusEntry, experimentEntry);
            if (!censusEntry.status().equals("COMPLETED")) {
                if (!experimentEntry.status().equals("CENSUS_UNRESOLVED") || experimentEntry.report() != null) {
                    throw new IllegalArgumentException("Census-censored specification must not be mutated");
                }
                censusCensored++;
                specifications.add(new SpecificationOutcome(
                        expected.id(),
                        expected.selection().gcdStratum(),
                        "CENSUS_CENSORED",
                        censusEntry.status(),
                        experimentEntry.status(),
                        0));
                continue;
            }
            censusCompleted++;
            List<GcdCensusSite> sites = censusEntry.report().sites();
            Map<String, MutationResult> results = gcdResults(experimentEntry);
            if (experimentEntry.status().equals("COMPLETED")) {
                experimentCompleted++;
                if (!results.keySet()
                        .equals(sites.stream()
                                .map(GcdCensusSite::candidateId)
                                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
                    throw new IllegalArgumentException("Prospective experiment GCD IDs differ from the frozen census");
                }
            } else {
                experimentCensored++;
                if (experimentEntry.report() != null || !results.isEmpty()) {
                    throw new IllegalArgumentException("Censored prospective experiment must not contain results");
                }
            }
            for (GcdCensusSite site : sites) {
                MutationResult result = results.get(site.candidateId());
                MutationResult terminal = result == null ? unresolved(experimentEntry, site) : result;
                outcomes.add(new GcdTerminalOutcome(expected.id(), terminal));
                if (result == null || !result.executionStatus().equals("COMPLETED")) {
                    unresolvedGcd++;
                } else {
                    completedGcd++;
                    eligible += isEligible(result) ? 1 : 0;
                }
            }
            specifications.add(new SpecificationOutcome(
                    expected.id(),
                    expected.selection().gcdStratum(),
                    experimentEntry.status().equals("COMPLETED") ? "COMPLETED" : "EXPERIMENT_CENSORED",
                    censusEntry.status(),
                    experimentEntry.status(),
                    sites.size()));
        }
        DiscoverySummary summary = new DiscoverySummary(
                manifest.entries().size(),
                censusCompleted,
                censusCensored,
                experimentCompleted,
                experimentCensored,
                outcomes.size(),
                completedGcd,
                unresolvedGcd,
                eligible);
        return new ProspectiveDiscoveryReport(
                1,
                manifest.profile(),
                manifest.repository(),
                manifest.commit(),
                census.receipt(),
                experimentReceipt,
                censusArtifact,
                specifications,
                outcomes,
                summary,
                census,
                experiment);
    }

    private static void requireOuterIdentity(
            CorpusManifest manifest, ProspectiveCensusReport census, CorpusExperimentReport experiment) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(census, "census");
        Objects.requireNonNull(experiment, "experiment");
        if (!manifest.profile().equals(census.census().profile())
                || !manifest.repository().equals(census.census().repository())
                || !manifest.repository().equals(experiment.repository())
                || !manifest.commit().equals(census.census().commit())
                || !manifest.commit().equals(experiment.commit())
                || manifest.entries().size() != census.census().entries().size()
                || manifest.entries().size() != experiment.entries().size()) {
            throw new IllegalArgumentException("Prospective manifest, census, and experiment identities differ");
        }
    }

    private static void requireEntryIdentity(
            CorpusManifest.Entry expected, CorpusCensusEntryResult census, CorpusEntryResult experiment) {
        if (!expected.id().equals(census.id())
                || !expected.id().equals(experiment.id())
                || !expected.domain().equals(census.domain())
                || !expected.domain().equals(experiment.domain())
                || !expected.selection().equals(census.selection())
                || (census.report() != null
                        && (!expected.rootModule().equals(census.report().module())
                                || !expected.config().equals(census.report().config())))
                || (experiment.report() != null
                        && (!expected.rootModule().equals(experiment.report().module())
                                || !expected.config().equals(experiment.report().config())))) {
            throw new IllegalArgumentException("Prospective entry identities differ");
        }
    }

    private static Map<String, MutationResult> gcdResults(CorpusEntryResult entry) {
        Map<String, MutationResult> results = new LinkedHashMap<>();
        if (entry.report() == null) {
            return results;
        }
        for (MutationResult result : entry.report().results()) {
            if (result.family().equals("GCD") && results.put(result.id(), result) != null) {
                throw new IllegalArgumentException("Prospective GCD result IDs must be unique per entry");
            }
        }
        return results;
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
                "Experiment entry status: " + entry.status(),
                0,
                0,
                null);
    }

    public static boolean isEligible(MutationResult result) {
        return result.executionStatus().equals("COMPLETED")
                && result.oracleOutcome().equals("PASSED")
                && Set.of("STATE_EXPANDING", "STATE_INCOMPARABLE").contains(result.stateRelation())
                && result.verdict().equals("state-invariant-gap-candidate")
                && result.mutantOnlyWitness() != null
                && !result.mutantOnlyWitness().isBlank();
    }

    public record SpecificationOutcome(
            String entryId,
            String riskClass,
            String status,
            String censusStatus,
            String experimentStatus,
            int censusSites) {
        public SpecificationOutcome {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(riskClass, "riskClass");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(censusStatus, "censusStatus");
            Objects.requireNonNull(experimentStatus, "experimentStatus");
            if (censusSites < 0) {
                throw new IllegalArgumentException("Prospective census site count must not be negative");
            }
        }
    }

    public record DiscoverySummary(
            int specifications,
            int censusCompletedSpecifications,
            int censusCensoredSpecifications,
            int experimentCompletedSpecifications,
            int experimentCensoredSpecifications,
            int censusSites,
            int completedGcdOutcomes,
            int unresolvedGcdOutcomes,
            int eligibleCandidates) {
        public DiscoverySummary {
            if (specifications < 0
                    || censusCompletedSpecifications < 0
                    || censusCensoredSpecifications < 0
                    || experimentCompletedSpecifications < 0
                    || experimentCensoredSpecifications < 0
                    || censusSites < 0
                    || completedGcdOutcomes < 0
                    || unresolvedGcdOutcomes < 0
                    || eligibleCandidates < 0
                    || censusCompletedSpecifications + censusCensoredSpecifications != specifications
                    || experimentCompletedSpecifications + experimentCensoredSpecifications
                            != censusCompletedSpecifications
                    || completedGcdOutcomes + unresolvedGcdOutcomes != censusSites
                    || eligibleCandidates > completedGcdOutcomes) {
                throw new IllegalArgumentException("Prospective discovery summary does not conserve denominators");
            }
        }
    }
}
