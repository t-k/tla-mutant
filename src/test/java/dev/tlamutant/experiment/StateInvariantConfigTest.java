package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StateInvariantConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void removesOnlyTemporalPropertiesAndRetainsStateInvariantConfiguration() throws Exception {
        Path source = temporaryDirectory.resolve("Model.cfg");
        Path target = temporaryDirectory.resolve("State.cfg");
        Files.writeString(source, """
                CONSTANT N = 3
                SPECIFICATION Spec
                INVARIANTS TypeOK Safety
                PROPERTY
                  Liveness
                  EventuallyDone
                CONSTRAINT StateConstraint
                CHECK_DEADLOCK FALSE
                """);

        StateInvariantConfig.write(source, target);

        assertEquals("""
                CONSTANT N = 3
                SPECIFICATION Spec
                INVARIANTS TypeOK Safety
                CONSTRAINT StateConstraint
                CHECK_DEADLOCK FALSE
                """, Files.readString(target));
    }

    @Test
    void writesOnlyThePreregisteredStateInvariantAllowlist() throws Exception {
        Path source = temporaryDirectory.resolve("Mission.cfg");
        Path target = temporaryDirectory.resolve("State.cfg");
        Files.writeString(source, """
                CONSTANT N = 3
                INIT Init
                NEXT Next
                INVARIANTS
                  TypeOK
                  Solution
                PROPERTY Liveness
                CHECK_DEADLOCK FALSE
                """);

        StateInvariantConfig.write(source, target, List.of("TypeOK"));

        assertEquals("""
                CONSTANT N = 3
                INIT Init
                NEXT Next
                CHECK_DEADLOCK FALSE
                INVARIANTS
                  TypeOK
                """, Files.readString(target));
    }

    @Test
    void rejectsUnsafeOrDuplicateAllowlistNamesBeforeWriting() throws Exception {
        Path source = temporaryDirectory.resolve("Model.cfg");
        Files.writeString(source, "SPECIFICATION Spec\nINVARIANT TypeOK\n");

        assertThrows(
                IllegalArgumentException.class,
                () -> StateInvariantConfig.write(
                        source, temporaryDirectory.resolve("Unsafe.cfg"), List.of("TypeOK\nINVARIANT Injected")));
        assertThrows(
                IllegalArgumentException.class,
                () -> StateInvariantConfig.write(
                        source, temporaryDirectory.resolve("Duplicate.cfg"), List.of("TypeOK", "TypeOK")));
    }

    @Test
    void writesFullTypeOnlyAndLegacyOracleConfigurationsToDistinctNewFiles() throws Exception {
        Path source = temporaryDirectory.resolve("Oracle.cfg");
        Files.writeString(source, "INIT Init\nNEXT Next\nINVARIANT Existing\n");
        Path full = temporaryDirectory.resolve("full.cfg");
        Path typeOnly = temporaryDirectory.resolve("type-only.cfg");
        Path legacy = temporaryDirectory.resolve("legacy.cfg");

        StateInvariantConfig.write(source, full, List.of("TypeOK", "LegacyOK", "AdoptedOK"));
        StateInvariantConfig.write(source, typeOnly, List.of("TypeOK"));
        StateInvariantConfig.write(source, legacy, List.of("TypeOK", "LegacyOK"));

        assertEquals("INIT Init\nNEXT Next\nINVARIANTS\n  TypeOK\n  LegacyOK\n  AdoptedOK\n", Files.readString(full));
        assertEquals("INIT Init\nNEXT Next\nINVARIANTS\n  TypeOK\n", Files.readString(typeOnly));
        assertEquals("INIT Init\nNEXT Next\nINVARIANTS\n  TypeOK\n  LegacyOK\n", Files.readString(legacy));
        assertThrows(
                java.nio.file.FileAlreadyExistsException.class,
                () -> StateInvariantConfig.write(source, full, List.of("TypeOK")));
    }
}
