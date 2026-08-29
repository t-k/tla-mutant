package dev.tlamutant.census;

import java.util.List;
import java.util.Objects;

public record GcdCensusReport(int schemaVersion, String module, String config, List<GcdCensusSite> sites) {
    public GcdCensusReport {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported GCD census schema");
        }
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(config, "config");
        sites = List.copyOf(sites);
    }
}
