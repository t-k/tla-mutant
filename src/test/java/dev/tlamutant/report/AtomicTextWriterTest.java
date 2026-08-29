package dev.tlamutant.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AtomicTextWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyReplacesTextWithoutLeavingATemporarySibling() throws Exception {
        Path output = temporaryDirectory.resolve("nested/report.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "stale");

        new AtomicTextWriter().write(output, "replacement\n");

        assertEquals("replacement\n", Files.readString(output));
        try (var siblings = Files.list(output.getParent())) {
            assertFalse(siblings.anyMatch(path -> path.getFileName().toString().startsWith(".text-")));
        }
    }
}
