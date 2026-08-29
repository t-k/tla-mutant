package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.tlamutant.tlc.ExecutionStatus;
import dev.tlamutant.tlc.OracleOutcome;
import dev.tlamutant.tlc.TlcExecution;
import java.util.List;
import org.junit.jupiter.api.Test;

final class KillAttributionTest {
    private static final InvariantOracleSets SETS =
            new InvariantOracleSets(List.of("TypeOK"), List.of("LegacyOK"), List.of("AdoptedOK"));

    @Test
    void classifiesTypeAdoptedLegacyAndUnresolvedEvidenceExclusively() {
        assertEquals(
                "type-invariant-kill",
                KillAttribution.classify(SETS, execution(OracleOutcome.KILLED_STATE_SAFETY), null, null)
                        .category());
        assertEquals(
                "adopted-target-invariant-kill",
                KillAttribution.classify(SETS, execution(OracleOutcome.PASSED), execution(OracleOutcome.PASSED), null)
                        .category());
        assertEquals(
                "legacy-target-invariant-kill",
                KillAttribution.classify(
                                SETS,
                                execution(OracleOutcome.PASSED),
                                execution(OracleOutcome.KILLED_STATE_SAFETY),
                                null)
                        .category());
        assertEquals(
                "kill-attribution-unresolved",
                KillAttribution.classify(SETS, timedOut(), null, null).category());
    }

    @Test
    void avoidsRedundantEvidenceWhenAnOracleSetAlreadyIdentifiesTheCategory() {
        InvariantOracleSets typeOnly = new InvariantOracleSets(List.of("TypeOK"), List.of(), List.of());
        InvariantOracleSets adoptedOnly = new InvariantOracleSets(List.of(), List.of(), List.of("AdoptedOK"));
        InvariantOracleSets legacyOnly = new InvariantOracleSets(List.of(), List.of("LegacyOK"), List.of());

        KillAttribution type = KillAttribution.classify(typeOnly, null, null, null);
        KillAttribution adopted = KillAttribution.classify(adoptedOnly, null, null, null);
        KillAttribution legacy = KillAttribution.classify(legacyOnly, null, null, null);

        assertEquals("type-invariant-kill", type.category());
        assertEquals("adopted-target-invariant-kill", adopted.category());
        assertEquals("legacy-target-invariant-kill", legacy.category());
        assertNull(type.typeOnlyExecutionStatus());
        assertNull(adopted.legacyExecutionStatus());
    }

    private static TlcExecution execution(OracleOutcome oracle) {
        return new TlcExecution(ExecutionStatus.COMPLETED, oracle, 0, List.of(), List.of(), "", "", false);
    }

    private static TlcExecution timedOut() {
        return new TlcExecution(
                ExecutionStatus.TIMEOUT, OracleOutcome.NOT_EVALUATED, -1, List.of(), List.of(), "", "", false);
    }
}
