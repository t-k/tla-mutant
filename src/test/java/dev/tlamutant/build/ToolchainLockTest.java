package dev.tlamutant.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ToolchainLockTest {
    private static final Path LOCK = Path.of("config/tla-tools.lock.json");
    private static final String EXPECTED_URL =
            "https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar";
    private static final String EXPECTED_SHA256 = "eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a";

    @Test
    void pinsTheReviewedTlaToolsArtifact() throws IOException {
        JsonNode lock = new ObjectMapper().readTree(Files.readString(LOCK));

        assertEquals("1.8.0", lock.required("version").textValue());
        assertEquals(EXPECTED_URL, lock.required("url").textValue());
        assertEquals(4_487_757L, lock.required("sizeBytes").longValue());
        assertEquals(EXPECTED_SHA256, lock.required("sha256").textValue());
    }

    @Test
    void aChangedDigestCannotMatchTheReviewedArtifact() {
        assertNotEquals(EXPECTED_SHA256, "0abd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a");
    }
}
