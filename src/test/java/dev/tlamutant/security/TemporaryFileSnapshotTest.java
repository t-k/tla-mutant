package dev.tlamutant.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TemporaryFileSnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void materializesExactSnapshotBytesAndRemovesItsPrivateDirectory() throws Exception {
        byte[] expected = "exact tool bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path source = temporaryDirectory.resolve("tool.jar");
        Files.write(source, expected);
        BoundedFileSnapshot snapshot = BoundedFileSnapshot.read(source, 100);
        Path copy;
        Path parent;

        try (TemporaryFileSnapshot temporary = TemporaryFileSnapshot.create(snapshot, "tla2tools.jar")) {
            copy = temporary.path();
            parent = copy.getParent();
            assertTrue(Files.isRegularFile(copy));
            assertArrayEquals(expected, Files.readAllBytes(copy));
        }

        assertFalse(Files.exists(copy));
        assertFalse(Files.exists(parent));
    }
}
