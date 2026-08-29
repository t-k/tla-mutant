package dev.tlamutant.prospective;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.CorpusCensusReport;
import dev.tlamutant.census.GcdCensusSite;
import dev.tlamutant.corpus.CorpusManifest;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ProspectiveCensusValidator {
    private static final Set<String> FAILURE_STATUSES =
            Set.of("TIMEOUT", "OUTPUT_LIMIT", "SANDBOX_ERROR", "INVALID_REPORT", "FETCH_OR_SANDBOX_ERROR");

    private ProspectiveCensusValidator() {}

    public static ProspectiveCensusReport validate(
            CorpusManifest manifest,
            ProspectiveCensusReport report,
            CensusInputIdentity.Artifact expectedReceipt,
            CensusInputIdentity expectedInputs) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(expectedReceipt, "expectedReceipt");
        Objects.requireNonNull(expectedInputs, "expectedInputs");
        CorpusCensusReport census = report.census();
        if (!report.receipt().equals(expectedReceipt)
                || census.schemaVersion() != 3
                || !expectedInputs.equals(census.inputs())
                || !manifest.profile().equals(census.profile())
                || !manifest.repository().equals(census.repository())
                || !manifest.commit().equals(census.commit())
                || census.entries().size() != manifest.entries().size()) {
            throw new IllegalArgumentException("Prospective census identity does not match its frozen inputs");
        }

        Set<String> candidateIds = new HashSet<>();
        for (int index = 0; index < manifest.entries().size(); index++) {
            CorpusManifest.Entry expected = manifest.entries().get(index);
            CorpusCensusEntryResult actual = census.entries().get(index);
            if (!expected.id().equals(actual.id())
                    || !expected.domain().equals(actual.domain())
                    || !expected.selection().equals(actual.selection())) {
                throw new IllegalArgumentException("Prospective census changed entry membership or order");
            }
            if (actual.status().equals("COMPLETED")) {
                if (actual.report() == null
                        || !expected.rootModule().equals(actual.report().module())
                        || !expected.config().equals(actual.report().config())) {
                    throw new IllegalArgumentException("Completed prospective census report identity is invalid");
                }
                for (GcdCensusSite site : actual.report().sites()) {
                    if (!candidateIds.add(site.candidateId())) {
                        throw new IllegalArgumentException("Prospective candidate IDs must be globally unique");
                    }
                }
            } else {
                validateFailure(actual);
            }
        }
        return report;
    }

    private static void validateFailure(CorpusCensusEntryResult entry) {
        if (!FAILURE_STATUSES.contains(entry.status()) || entry.report() != null) {
            throw new IllegalArgumentException("Prospective census failure status is unknown");
        }
        ProspectiveText.requireSafeMultiline(entry.diagnostic(), "census diagnostic", 2000);
    }
}
