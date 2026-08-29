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
}
