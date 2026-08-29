package dev.tlamutant.experiment;

import java.util.Objects;

public record GcdOutcomeSummary(
        String grouping,
        String value,
        int specifications,
        int totalSites,
        int killedUnattributed,
        int killedAdoptedTarget,
        int killedLegacyTarget,
        int killedTypeInvariant,
        int killAttributionUnresolved,
        int stateEqual,
        int stateExpanding,
        int stateIncomparable,
        int stateRestricting,
        int invalid,
        int unresolved,
        int witnessBearingCandidates,
        int specificationsKilledUnattributed,
        int specificationsKilledAdoptedTarget,
        int specificationsKilledLegacyTarget,
        int specificationsKilledTypeInvariant,
        int specificationsKillAttributionUnresolved,
        int specificationsStateEqual,
        int specificationsStateExpanding,
        int specificationsStateIncomparable,
        int specificationsStateRestricting,
        int specificationsInvalid,
        int specificationsUnresolved,
        int specificationsWithWitness) {
    public GcdOutcomeSummary {
        Objects.requireNonNull(grouping, "grouping");
        Objects.requireNonNull(value, "value");
        if (specifications < 0
                || totalSites < 0
                || killedUnattributed < 0
                || killedAdoptedTarget < 0
                || killedLegacyTarget < 0
                || killedTypeInvariant < 0
                || killAttributionUnresolved < 0
                || stateEqual < 0
                || stateExpanding < 0
                || stateIncomparable < 0
                || stateRestricting < 0
                || invalid < 0
                || unresolved < 0
                || witnessBearingCandidates < 0
                || specificationsKilledUnattributed < 0
                || specificationsKilledAdoptedTarget < 0
                || specificationsKilledLegacyTarget < 0
                || specificationsKilledTypeInvariant < 0
                || specificationsKillAttributionUnresolved < 0
                || specificationsStateEqual < 0
                || specificationsStateExpanding < 0
                || specificationsStateIncomparable < 0
                || specificationsStateRestricting < 0
                || specificationsInvalid < 0
                || specificationsUnresolved < 0
                || specificationsWithWitness < 0
                || specificationsKilledUnattributed > specifications
                || specificationsKilledAdoptedTarget > specifications
                || specificationsKilledLegacyTarget > specifications
                || specificationsKilledTypeInvariant > specifications
                || specificationsKillAttributionUnresolved > specifications
                || specificationsStateEqual > specifications
                || specificationsStateExpanding > specifications
                || specificationsStateIncomparable > specifications
                || specificationsStateRestricting > specifications
                || specificationsInvalid > specifications
                || specificationsUnresolved > specifications
                || specificationsWithWitness > specifications
                || totalSites
                        != killedUnattributed
                                + killedAdoptedTarget
                                + killedLegacyTarget
                                + killedTypeInvariant
                                + killAttributionUnresolved
                                + stateEqual
                                + stateExpanding
                                + stateIncomparable
                                + stateRestricting
                                + invalid
                                + unresolved) {
            throw new IllegalArgumentException("Invalid or non-conserving GCD outcome summary");
        }
    }

    public GcdOutcomeSummary(
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
            int witnessBearingCandidates,
            int specificationsKilledUnattributed,
            int specificationsStateEqual,
            int specificationsStateExpanding,
            int specificationsStateIncomparable,
            int specificationsStateRestricting,
            int specificationsInvalid,
            int specificationsUnresolved,
            int specificationsWithWitness) {
        this(
                grouping,
                value,
                specifications,
                totalSites,
                killedUnattributed,
                0,
                0,
                0,
                0,
                stateEqual,
                stateExpanding,
                stateIncomparable,
                stateRestricting,
                invalid,
                unresolved,
                witnessBearingCandidates,
                specificationsKilledUnattributed,
                0,
                0,
                0,
                0,
                specificationsStateEqual,
                specificationsStateExpanding,
                specificationsStateIncomparable,
                specificationsStateRestricting,
                specificationsInvalid,
                specificationsUnresolved,
                specificationsWithWitness);
    }
}
