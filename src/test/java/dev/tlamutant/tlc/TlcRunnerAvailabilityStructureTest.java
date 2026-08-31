package dev.tlamutant.tlc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class TlcRunnerAvailabilityStructureTest {
    @Test
    void everyCollectorAndExecutorWaitHasAnExplicitTimeout() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/tlamutant/tlc/TlcRunner.java"), UTF_8);

        assertFalse(source.contains("try (var executor"));
        assertFalse(Pattern.compile("\\.get\\(\\s*\\)").matcher(source).find());
        assertFalse(
                Pattern.compile("process\\.waitFor\\(\\s*\\)").matcher(source).find());
        assertTrue(source.contains("awaitTermination("));
    }
}
