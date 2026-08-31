package dev.tlamutant.tlc;

import dev.tlamutant.state.CanonicalState;
import java.util.List;
import java.util.Objects;

public record TlcExecution(
        ExecutionStatus status,
        OracleOutcome oracle,
        int exitCode,
        List<TlcToolEvent> events,
        List<CanonicalState> states,
        String stdout,
        String stderr,
        boolean outputTruncated,
        String violatedProperty) {
    public TlcExecution {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(oracle, "oracle");
        events = List.copyOf(events);
        states = List.copyOf(states);
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }

    public TlcExecution(
            ExecutionStatus status,
            OracleOutcome oracle,
            int exitCode,
            List<TlcToolEvent> events,
            List<CanonicalState> states,
            String stdout,
            String stderr,
            boolean outputTruncated) {
        this(status, oracle, exitCode, events, states, stdout, stderr, outputTruncated, null);
    }
}
