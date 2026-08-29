package dev.tlamutant.census;

import dev.tlamutant.corpus.CorpusManifest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CensusConformanceValidator {
    private CensusConformanceValidator() {}

    public static CorpusCensusReport validateBoundInputs(
            CorpusManifest manifest, CorpusCensusReport census, CensusInputIdentity expectedInputs) {
        if (census.schemaVersion() != 3 || !Objects.equals(census.inputs(), expectedInputs)) {
            throw new IllegalArgumentException("Census inputs do not match the exact experiment inputs");
        }
        return validate(manifest, census);
    }

    public static CorpusCensusReport validate(CorpusManifest manifest, CorpusCensusReport census) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(census, "census");
        if (!Objects.equals(manifest.profile(), census.profile())
                || !Objects.equals(manifest.repository(), census.repository())
                || !Objects.equals(manifest.commit(), census.commit())
                || manifest.entries().size() != census.entries().size()) {
            throw new IllegalArgumentException("Census provenance does not match the preregistered manifest");
        }

        Set<String> candidateIds = new HashSet<>();
        Map<String, Integer> clusterSiteCounts = new HashMap<>();
        int totalSites = 0;
        for (int index = 0; index < manifest.entries().size(); index++) {
            CorpusManifest.Entry expected = manifest.entries().get(index);
            CorpusCensusEntryResult actual = census.entries().get(index);
            if (!expected.id().equals(actual.id())
                    || !expected.domain().equals(actual.domain())
                    || !expected.selection().equals(actual.selection())) {
                throw new IllegalArgumentException("Census entry identity or selection metadata does not match");
            }
            if (!actual.status().equals("COMPLETED")) {
                throw new IllegalArgumentException("Every preregistered census entry must complete");
            }
            GcdCensusReport report = actual.report();
            if (!expected.rootModule().equals(report.module())
                    || !expected.config().equals(report.config())) {
                throw new IllegalArgumentException("Per-entry census module identity does not match");
            }
            if (expected.selection().cohort().equals("gcd-enriched")
                    && report.sites().stream().noneMatch(GcdCensusSite::conforms)) {
                throw new IllegalArgumentException("Every enriched entry requires a conforming GCD site");
            }
            for (GcdCensusSite site : report.sites()) {
                if (!candidateIds.add(site.candidateId())) {
                    throw new IllegalArgumentException("GCD census candidate IDs must be globally unique");
                }
            }
            int count = report.sites().size();
            totalSites = Math.addExact(totalSites, count);
            clusterSiteCounts.merge(expected.selection().familyCluster(), count, Math::addExact);
        }
        if (totalSites == 0) {
            throw new IllegalArgumentException("The preregistered corpus must contain at least one GCD site");
        }
        for (int count : clusterSiteCounts.values()) {
            if (Math.multiplyExact(count, 4) > totalSites) {
                throw new IllegalArgumentException("A family cluster exceeds the 25 percent GCD-site limit");
            }
        }
        return census;
    }
}
