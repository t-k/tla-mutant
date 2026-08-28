package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
