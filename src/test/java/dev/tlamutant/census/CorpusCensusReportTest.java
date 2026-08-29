package dev.tlamutant.census;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CorpusCensusReportTest {
    private static final CensusInputIdentity INPUTS = new CensusInputIdentity(
            new CensusInputIdentity.Artifact("a".repeat(64), 10),
            new CensusInputIdentity.Artifact("b".repeat(64), 20),
            new CensusInputIdentity.Artifact("c".repeat(64), 30));
    private static final CensusInputIdentity COMPLETE_INPUTS = new CensusInputIdentity(
            new CensusInputIdentity.Artifact("a".repeat(64), 10),
            new CensusInputIdentity.Artifact("b".repeat(64), 20),
            new CensusInputIdentity.Artifact("c".repeat(64), 30),
            new CensusInputIdentity.Artifact("d".repeat(64), 40));

    @Test
    void keepsSchemaOneAuditCompatibilityAndRequiresIdentityForSchemaTwo() {
        assertDoesNotThrow(() -> new CorpusCensusReport(1, "gcd-stratified-v2", "repository", "commit", List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusCensusReport(2, "gcd-stratified-v3", "repository", "commit", null, List.of()));
        assertDoesNotThrow(
                () -> new CorpusCensusReport(2, "gcd-stratified-v3", "repository", "commit", INPUTS, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusCensusReport(3, "gcd-stratified-v6", "repository", "commit", INPUTS, List.of()));
        assertDoesNotThrow(() ->
                new CorpusCensusReport(3, "gcd-stratified-v6", "repository", "commit", COMPLETE_INPUTS, List.of()));
    }

    @Test
    void rejectsMalformedInputIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new CensusInputIdentity.Artifact("A".repeat(64), 10));
        assertThrows(IllegalArgumentException.class, () -> new CensusInputIdentity.Artifact("a".repeat(64), 0));
    }
}
