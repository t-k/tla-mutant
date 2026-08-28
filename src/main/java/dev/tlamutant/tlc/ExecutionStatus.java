package dev.tlamutant.tlc;

public enum ExecutionStatus {
    COMPLETED,
    INVALID_SYNTAX,
    INVALID_SEMANTIC,
    EVALUATION_ERROR,
    TIMEOUT,
    OUT_OF_MEMORY,
    TOOL_ERROR
}
