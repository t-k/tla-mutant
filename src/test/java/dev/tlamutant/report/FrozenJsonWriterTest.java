package dev.tlamutant.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FrozenJsonWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsOnceAndNeverReplacesAnExistingFrozenArtifact() throws IOException {
        Path output = temporaryDirectory.resolve("frozen.json");
        FrozenJsonWriter writer = new FrozenJsonWriter();
        writer.writeNew(output, Map.of("schemaVersion", 2));
        byte[] original = Files.readAllBytes(output);

        assertThrows(IOException.class, () -> writer.writeNew(output, Map.of("schemaVersion", 3)));
        assertEquals(
                java.util.HexFormat.of().formatHex(original),
                java.util.HexFormat.of().formatHex(Files.readAllBytes(output)));
        try (var paths = Files.list(temporaryDirectory)) {
            assertEquals(1, paths.count());
        }
    }
}
