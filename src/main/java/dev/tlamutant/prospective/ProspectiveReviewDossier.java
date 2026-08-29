package dev.tlamutant.prospective;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.experiment.GcdTerminalOutcome;
import dev.tlamutant.experiment.KillAttribution;
import dev.tlamutant.experiment.MutationResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ProspectiveReviewDossier(
        int schemaVersion,
        String profile,
        CensusInputIdentity.Artifact protocolArtifact,
        CensusInputIdentity.Artifact manifestArtifact,
        CensusInputIdentity.Artifact censusArtifact,
        CensusInputIdentity.Artifact discoveryArtifact,
        List<Candidate> candidates) {
    public ProspectiveReviewDossier {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Prospective review dossier schemaVersion must be 1");
        }
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(protocolArtifact, "protocolArtifact");
        Objects.requireNonNull(manifestArtifact, "manifestArtifact");
        Objects.requireNonNull(censusArtifact, "censusArtifact");
        Objects.requireNonNull(discoveryArtifact, "discoveryArtifact");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Set<CandidateKey> keys = new HashSet<>();
        CandidateKey previous = null;
        for (Candidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (!keys.add(candidate.key())) {
                throw new IllegalArgumentException("Prospective dossier candidate keys must be unique");
            }
            if (previous != null && previous.compareTo(candidate.key()) >= 0) {
                throw new IllegalArgumentException("Prospective dossier candidates must use canonical key order");
            }
            previous = candidate.key();
        }
    }

    public static ProspectiveReviewDossier derive(
            CorpusManifest manifest,
            ProspectiveDiscoveryReport report,
            CensusInputIdentity.Artifact protocolArtifact,
            CensusInputIdentity.Artifact manifestArtifact,
            CensusInputIdentity.Artifact discoveryArtifact) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(report, "report");
        if (!manifest.profile().equals(report.profile())
                || !manifest.repository().equals(report.repository())
                || !manifest.commit().equals(report.commit())) {
            throw new IllegalArgumentException("Prospective dossier manifest identity differs from discovery");
        }
        Map<String, CorpusManifest.Entry> entries = new HashMap<>();
        for (CorpusManifest.Entry entry : manifest.entries()) {
            if (entries.put(entry.id(), entry) != null) {
                throw new IllegalArgumentException("Prospective manifest entry IDs must be unique");
            }
        }
        Set<CandidateKey> keys = new HashSet<>();
        List<Candidate> candidates = new ArrayList<>();
        for (GcdTerminalOutcome outcome : report.gcdOutcomes()) {
            CandidateKey key =
                    new CandidateKey(outcome.entryId(), outcome.result().id());
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Prospective discovery candidate keys must be unique");
            }
            if (!isEligible(outcome.result())) {
                continue;
            }
            CorpusManifest.Entry entry = entries.get(outcome.entryId());
            if (entry == null) {
                throw new IllegalArgumentException("Prospective discovery references an unknown manifest entry");
            }
            candidates.add(Candidate.from(key, entry, outcome.result()));
        }
        candidates.sort(Comparator.comparing(Candidate::key));
        if (candidates.size() != report.summary().eligibleCandidates()) {
            throw new IllegalArgumentException("Prospective dossier does not match the discovery eligible count");
        }
        return new ProspectiveReviewDossier(
                1,
                report.profile(),
                protocolArtifact,
                manifestArtifact,
                report.censusArtifact(),
                discoveryArtifact,
                candidates);
    }

    public static boolean isEligible(MutationResult result) {
        return ProspectiveDiscoveryReport.isEligible(result);
    }

    public record CandidateKey(String entryId, String candidateId) implements Comparable<CandidateKey> {
        public CandidateKey {
            if (entryId == null
                    || !entryId.matches("[a-z0-9][a-z0-9-]{0,63}")
                    || candidateId == null
                    || !candidateId.matches("gcd-[0-9a-f]{20}")) {
                throw new IllegalArgumentException("Prospective candidate key is invalid");
            }
        }

        @Override
        public int compareTo(CandidateKey other) {
            return Comparator.comparing(CandidateKey::entryId)
                    .thenComparing(CandidateKey::candidateId)
                    .compare(this, other);
        }
    }

    public record Candidate(
            CandidateKey key,
            String domain,
            String riskClass,
            String rootModule,
            List<CorpusManifest.SourceFile> sourceFiles,
            String definition,
            String originalGuard,
            String replacement,
            String executionStatus,
            String oracleOutcome,
            String stateRelation,
            String verdict,
            int baselineStateCount,
            int mutantStateCount,
            String mutantOnlyWitness,
            KillAttribution attribution) {
        public Candidate {
            Objects.requireNonNull(key, "key");
            ProspectiveText.requireSafeLine(domain, "domain", 128);
            ProspectiveText.requireSafeLine(riskClass, "riskClass", 64);
            ProspectiveText.requireSafeLine(rootModule, "rootModule", 256);
            sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
            ProspectiveText.requireSafeLine(definition, "definition", 256);
            ProspectiveText.requireSafeMultiline(originalGuard, "originalGuard", 2000);
            ProspectiveText.requireSafeMultiline(replacement, "replacement", 2000);
            ProspectiveText.requireSafeLine(executionStatus, "executionStatus", 64);
            ProspectiveText.requireSafeLine(oracleOutcome, "oracleOutcome", 64);
            ProspectiveText.requireSafeLine(stateRelation, "stateRelation", 64);
            ProspectiveText.requireSafeLine(verdict, "verdict", 128);
            ProspectiveText.requireSafeMultiline(mutantOnlyWitness, "mutantOnlyWitness", 16_384);
            if (baselineStateCount < 0 || mutantStateCount < 0) {
                throw new IllegalArgumentException("Prospective candidate state counts must not be negative");
            }
        }

        private static Candidate from(CandidateKey key, CorpusManifest.Entry entry, MutationResult result) {
            return new Candidate(
                    key,
                    entry.domain(),
                    entry.selection().gcdStratum(),
                    entry.rootModule(),
                    entry.files(),
                    result.definition(),
                    result.original(),
                    result.replacement(),
                    result.executionStatus(),
                    result.oracleOutcome(),
                    result.stateRelation(),
                    result.verdict(),
                    result.baselineStateCount(),
                    result.mutantStateCount(),
                    result.mutantOnlyWitness(),
                    result.attribution());
        }
    }
}
