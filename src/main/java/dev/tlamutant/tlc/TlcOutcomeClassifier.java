package dev.tlamutant.tlc;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TlcOutcomeClassifier {
    private static final Set<Integer> OUT_OF_MEMORY_EVENTS = Set.of(1001, 1002, 1003);
    private static final Set<Integer> EVALUATION_ERROR_EVENTS = Set.of(2111, 2113);
    private static final Set<Integer> SAFETY_VIOLATION_EVENTS = Set.of(2107, 2108, 2110, 2112);
    private static final Set<Integer> FULL_PROPERTY_POSITIVE_EVENTS = java.util.stream.Stream.of(
                    OUT_OF_MEMORY_EVENTS,
                    EVALUATION_ERROR_EVENTS,
                    SAFETY_VIOLATION_EVENTS,
                    Set.of(2114, 2116, 2121, 2217, 2218, 2264))
            .flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Pattern TEMPORAL_NAME =
            Pattern.compile("^Temporal property ([A-Za-z_][A-Za-z0-9_]{0,127}) was violated\\.?$");
    private static final List<Pattern> SAFETY_NAMES = List.of(
            Pattern.compile("^Invariant ([A-Za-z_][A-Za-z0-9_]{0,127}) is violated(?: by the initial state)?\\.?$"),
            Pattern.compile("^Property ([A-Za-z_][A-Za-z0-9_]{0,127}) is violated(?: by the initial state)?\\.?$"),
            Pattern.compile("^Action property ([A-Za-z_][A-Za-z0-9_]{0,127}) is violated\\.?$"));

    Classification classify(boolean completed, int exitCode, List<TlcToolEvent> events, boolean completeStateFile) {
        return classify(TlcOracleProfile.FULL_PROPERTY, completed, exitCode, events, completeStateFile, false);
    }

    Classification classify(
            boolean completed,
            int exitCode,
            List<TlcToolEvent> events,
            boolean completeStateFile,
            boolean outputTruncated) {
        return classify(
                TlcOracleProfile.FULL_PROPERTY, completed, exitCode, events, completeStateFile, outputTruncated);
    }

    Classification classify(
            TlcOracleProfile profile,
            boolean completed,
            int exitCode,
            List<TlcToolEvent> events,
            boolean completeStateFile,
            boolean outputTruncated) {
        if (!completed) {
            return notEvaluated(ExecutionStatus.TIMEOUT);
        }
        if (profile == TlcOracleProfile.LEGACY) {
            return classifyLegacy(exitCode, events, completeStateFile);
        }
        if (outputTruncated
                || events.stream()
                        .anyMatch(event ->
                                event.severity() > 0 && !FULL_PROPERTY_POSITIVE_EVENTS.contains(event.code()))) {
            return notEvaluated(ExecutionStatus.TOOL_ERROR);
        }
        if (containsAnyError(events, OUT_OF_MEMORY_EVENTS)) {
            return notEvaluated(ExecutionStatus.OUT_OF_MEMORY);
        }
        if (containsAnyError(events, EVALUATION_ERROR_EVENTS)) {
            return notEvaluated(ExecutionStatus.EVALUATION_ERROR);
        }
        TlcToolEvent safety = firstError(events, SAFETY_VIOLATION_EVENTS);
        if (safety != null) {
            return new Classification(
                    ExecutionStatus.COMPLETED, OracleOutcome.KILLED_STATE_SAFETY, extractSafetyName(safety.body()));
        }
        TlcToolEvent temporal = firstError(events, Set.of(2116));
        if (temporal != null) {
            String property = extract(TEMPORAL_NAME, temporal.body());
            return new Classification(
                    ExecutionStatus.COMPLETED,
                    property == null ? OracleOutcome.KILLED_TEMPORAL_GENERIC : OracleOutcome.KILLED_TEMPORAL_NAMED,
                    property);
        }
        if (firstError(events, Set.of(2114)) != null) {
            return new Classification(ExecutionStatus.COMPLETED, OracleOutcome.KILLED_DEADLOCK, null);
        }
        if (first(events, Set.of(2193)) != null && exitCode == 0 && completeStateFile) {
            return new Classification(ExecutionStatus.COMPLETED, OracleOutcome.PASSED, null);
        }
        return notEvaluated(ExecutionStatus.TOOL_ERROR);
    }

    private Classification classifyLegacy(int exitCode, List<TlcToolEvent> events, boolean completeStateFile) {
        if (first(events, Set.of(1001)) != null) {
            return notEvaluated(ExecutionStatus.OUT_OF_MEMORY);
        }
        if (first(events, Set.of(2111)) != null) {
            return notEvaluated(ExecutionStatus.EVALUATION_ERROR);
        }
        TlcToolEvent safety = first(events, Set.of(2107, 2110));
        if (safety != null) {
            return new Classification(
                    ExecutionStatus.COMPLETED, OracleOutcome.KILLED_STATE_SAFETY, extractSafetyName(safety.body()));
        }
        if (first(events, Set.of(2114)) != null) {
            return new Classification(ExecutionStatus.COMPLETED, OracleOutcome.KILLED_DEADLOCK, null);
        }
        if (first(events, Set.of(2193)) != null && exitCode == 0 && completeStateFile) {
            return new Classification(ExecutionStatus.COMPLETED, OracleOutcome.PASSED, null);
        }
        return notEvaluated(ExecutionStatus.TOOL_ERROR);
    }

    private static Classification notEvaluated(ExecutionStatus status) {
        return new Classification(status, OracleOutcome.NOT_EVALUATED, null);
    }

    private static boolean containsAnyError(List<TlcToolEvent> events, Set<Integer> codes) {
        return firstError(events, codes) != null;
    }

    private static TlcToolEvent firstError(List<TlcToolEvent> events, Set<Integer> codes) {
        return events.stream()
                .filter(event -> event.severity() > 0 && codes.contains(event.code()))
                .findFirst()
                .orElse(null);
    }

    private static TlcToolEvent first(List<TlcToolEvent> events, Set<Integer> codes) {
        return events.stream()
                .filter(event -> codes.contains(event.code()))
                .findFirst()
                .orElse(null);
    }

    private static String extractSafetyName(String body) {
        for (Pattern pattern : SAFETY_NAMES) {
            String name = extract(pattern, body);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    private static String extract(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    record Classification(ExecutionStatus status, OracleOutcome oracle, String violatedProperty) {}
}
