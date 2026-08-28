package dev.tlamutant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SafeRelativePathTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsAPortableNormalizedRelativePath() {
        assertEquals(
                "specs/Mutex-1/Model.cfg",
                SafeRelativePath.parse("specs/Mutex-1/Model.cfg").value());
    }

    @Test
    void rejectsUnsafeOrAmbiguousNames() {
        for (String value : new String[] {
            "/etc/passwd", "../secret", "a/../b", "-config", "a/-config", "a\\b", "a\0b", "é.tla", "a//b"
        }) {
            assertThrows(IllegalArgumentException.class, () -> SafeRelativePath.parse(value), value);
        }
    }

    @Test
    void resolvesOnlyRegularSingleLinkFilesBelowTheRoot() throws IOException {
        Path file = temporaryDirectory.resolve("Model.tla");
        Files.writeString(file, "---- MODULE Model ----\n====\n");
        SafeRelativePath safe = SafeRelativePath.parse("Model.tla");

        assertEquals(file, safe.resolveRegularFile(temporaryDirectory));

        Path symlink = temporaryDirectory.resolve("Link.tla");
        Files.createSymbolicLink(symlink, Path.of("Model.tla"));
        assertThrows(IllegalArgumentException.class, () -> SafeRelativePath.parse("Link.tla")
                .resolveRegularFile(temporaryDirectory));

        Path hardlink = temporaryDirectory.resolve("Hard.tla");
        Files.createLink(hardlink, file);
        assertThrows(IllegalArgumentException.class, () -> safe.resolveRegularFile(temporaryDirectory));
    }
}
