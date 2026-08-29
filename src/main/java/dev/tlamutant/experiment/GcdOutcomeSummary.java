package dev.tlamutant.experiment;

import java.util.Objects;

public record GcdOutcomeSummary(
        String grouping,
        String value,
        int specifications,
        int totalSites,
        int killedUnattributed,
        int stateEqual,
        int stateExpanding,
        int stateIncomparable,
        int stateRestricting,
        int invalid,
        int unresolved,
        int witnessBearingCandidates) {
    public GcdOutcomeSummary {
        Objects.requireNonNull(grouping, "grouping");
        Objects.requireNonNull(value, "value");
        if (specifications < 0
                || totalSites < 0
                || killedUnattributed < 0
                || stateEqual < 0
                || stateExpanding < 0
                || stateIncomparable < 0
                || stateRestricting < 0
                || invalid < 0
                || unresolved < 0
                || witnessBearingCandidates < 0
                || totalSites
                        != killedUnattributed
                                + stateEqual
                                + stateExpanding
                                + stateIncomparable
                                + stateRestricting
                                + invalid
                                + unresolved) {
            throw new IllegalArgumentException("Invalid or non-conserving GCD outcome summary");
        }
    }
}
