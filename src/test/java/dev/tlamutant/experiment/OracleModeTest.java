package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class OracleModeTest {
    @Test
    void parsesOnlyTheTwoStableCliNames() {
        assertEquals(OracleMode.STATE_INVARIANTS, OracleMode.parse("state-invariants"));
        assertEquals(OracleMode.FULL_PROPERTY, OracleMode.parse("full-property"));
        assertThrows(IllegalArgumentException.class, () -> OracleMode.parse("FULL_PROPERTY"));
        assertThrows(IllegalArgumentException.class, () -> OracleMode.parse("full-properties"));
    }
}
