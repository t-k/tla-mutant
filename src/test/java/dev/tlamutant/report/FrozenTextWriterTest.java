package dev.tlamutant.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FrozenTextWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesUtf8TextOnceAndRefusesReplacement() throws Exception {
        Path output = temporaryDirectory.resolve("nested/report.md");
        FrozenTextWriter writer = new FrozenTextWriter();

        writer.writeNew(output, "Prospective result: 日本語\n");

        assertEquals("Prospective result: 日本語\n", Files.readString(output));
        assertThrows(FileAlreadyExistsException.class, () -> writer.writeNew(output, "replacement"));
        assertEquals("Prospective result: 日本語\n", Files.readString(output));
    }
}
