package dev.tlamutant.prospective;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.census.CorpusCensusReport;
import java.util.Objects;

public record ProspectiveCensusReport(
        int schemaVersion, CensusInputIdentity.Artifact receipt, CorpusCensusReport census) {
    public ProspectiveCensusReport {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Prospective census schemaVersion must be 1");
        }
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(census, "census");
    }
}
