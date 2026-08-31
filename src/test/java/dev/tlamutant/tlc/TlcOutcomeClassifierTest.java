package dev.tlamutant.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TlcOutcomeClassifierTest {
    private final TlcOutcomeClassifier classifier = new TlcOutcomeClassifier();

    @Test
    void classifiesNamedAndGenericTemporalViolationsFromStructuredEvents() {
        TlcOutcomeClassifier.Classification named = classifier.classify(
                true,
                13,
                List.of(new TlcToolEvent(2116, 1, "Temporal property Safe was violated.")),
                false);
        TlcOutcomeClassifier.Classification generic = classifier.classify(
                true,
                12,
                List.of(new TlcToolEvent(2116, 1, "Temporal properties were violated.")),
                false);

        assertEquals(ExecutionStatus.COMPLETED, named.status());
        assertEquals(OracleOutcome.KILLED_TEMPORAL_NAMED, named.oracle());
        assertEquals("Safe", named.violatedProperty());
        assertEquals(ExecutionStatus.COMPLETED, generic.status());
        assertEquals(OracleOutcome.KILLED_TEMPORAL_GENERIC, generic.oracle());
        assertNull(generic.violatedProperty());
    }

    @Test
    void rejectsUnsafeOrAmbiguousTemporalPropertyNames() {
        for (String body : List.of(
                "Temporal property Unsafe-Name was violated.",
                "prefix Temporal property Safe was violated.",
                "Temporal property Safe was violated. trailing",
                "Temporal property " + "x".repeat(129) + " was violated.")) {
            TlcOutcomeClassifier.Classification classification =
                    classifier.classify(true, 13, List.of(new TlcToolEvent(2116, 1, body)), false);

            assertEquals(OracleOutcome.KILLED_TEMPORAL_GENERIC, classification.oracle());
            assertNull(classification.violatedProperty());
        }
    }

    @Test
    void classifiesEveryConfiguredSafetyViolationAndCapturesItsName() {
        List<Case> cases = List.of(
                new Case(2107, "Invariant TypeOK is violated by the initial state", "TypeOK"),
                new Case(2108, "Property InitiallySafe is violated by the initial state", "InitiallySafe"),
                new Case(2110, "Invariant Safe is violated.", "Safe"),
                new Case(2112, "Action property LegalStep is violated.", "LegalStep"));

        for (Case testCase : cases) {
            TlcOutcomeClassifier.Classification classification = classifier.classify(
                    true,
                    12,
                    List.of(new TlcToolEvent(testCase.code(), 1, testCase.body())),
                    false);

            assertEquals(ExecutionStatus.COMPLETED, classification.status());
            assertEquals(OracleOutcome.KILLED_STATE_SAFETY, classification.oracle());
            assertEquals(testCase.property(), classification.violatedProperty());
        }
    }

    @Test
    void infrastructureFailuresNeverCountAsKills() {
        for (int code : List.of(1001, 1002, 1003)) {
            TlcOutcomeClassifier.Classification classification =
                    classifier.classify(true, 13, List.of(new TlcToolEvent(code, 1, "out of memory")), false);

            assertEquals(ExecutionStatus.OUT_OF_MEMORY, classification.status());
            assertEquals(OracleOutcome.NOT_EVALUATED, classification.oracle());
        }
        for (int code : List.of(2111, 2113)) {
            TlcOutcomeClassifier.Classification classification =
                    classifier.classify(true, 13, List.of(new TlcToolEvent(code, 1, "evaluation failed")), false);

            assertEquals(ExecutionStatus.EVALUATION_ERROR, classification.status());
            assertEquals(OracleOutcome.NOT_EVALUATED, classification.oracle());
        }

        assertEquals(
                OracleOutcome.NOT_EVALUATED,
                classifier.classify(true, 13, List.of(), false).oracle());
        assertEquals(
                ExecutionStatus.TIMEOUT,
                classifier.classify(false, -1, List.of(new TlcToolEvent(2116, 1, "Temporal properties were violated.")), false)
                        .status());
    }

    @Test
    void recognizesDeadlockAndRequiresAllSuccessSignalsForPass() {
        TlcOutcomeClassifier.Classification deadlock = classifier.classify(
                true, 12, List.of(new TlcToolEvent(2114, 1, "Deadlock reached.")), false);
        TlcOutcomeClassifier.Classification passed = classifier.classify(
                true, 0, List.of(new TlcToolEvent(2193, 0, "Model checking completed.")), true);
        TlcOutcomeClassifier.Classification incompleteStateFile = classifier.classify(
                true, 0, List.of(new TlcToolEvent(2193, 0, "Model checking completed.")), false);
        TlcOutcomeClassifier.Classification nonzeroExit = classifier.classify(
                true, 13, List.of(new TlcToolEvent(2193, 0, "Model checking completed.")), true);

        assertEquals(OracleOutcome.KILLED_DEADLOCK, deadlock.oracle());
        assertEquals(OracleOutcome.PASSED, passed.oracle());
        assertEquals(ExecutionStatus.TOOL_ERROR, incompleteStateFile.status());
        assertEquals(OracleOutcome.NOT_EVALUATED, incompleteStateFile.oracle());
        assertEquals(ExecutionStatus.TOOL_ERROR, nonzeroExit.status());
        assertEquals(OracleOutcome.NOT_EVALUATED, nonzeroExit.oracle());
    }

    private record Case(int code, String body, String property) {}
}
