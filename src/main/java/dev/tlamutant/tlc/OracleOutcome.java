package dev.tlamutant.tlc;

public enum OracleOutcome {
    PASSED,
    KILLED_STATE_SAFETY,
    KILLED_TEMPORAL_NAMED,
    KILLED_TEMPORAL_GENERIC,
    KILLED_DEADLOCK,
    NOT_EVALUATED
}
