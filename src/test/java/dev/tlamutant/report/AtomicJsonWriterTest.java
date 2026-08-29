package dev.tlamutant.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AtomicJsonWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyReplacesTheTargetWithoutLeavingATemporarySibling() throws Exception {
        Path output = temporaryDirectory.resolve("nested/census.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "stale");

        new AtomicJsonWriter().write(output, Map.of("schemaVersion", 1, "profile", "gcd-stratified-v1"));

        assertEquals(
                1,
                new ObjectMapper()
                        .readTree(output.toFile())
                        .required("schemaVersion")
                        .intValue());
        try (var siblings = Files.list(output.getParent())) {
            assertFalse(siblings.anyMatch(path -> path.getFileName().toString().startsWith(".json-")));
        }
    }
}
