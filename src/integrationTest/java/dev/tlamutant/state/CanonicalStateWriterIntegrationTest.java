package dev.tlamutant.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CanonicalStateWriterIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void repeatedRealTlcRunsEmitIdenticalCompleteStates() throws Exception {
        List<JsonNode> first = runTlc(temporaryDirectory.resolve("first.jsonl"));
        List<JsonNode> second = runTlc(temporaryDirectory.resolve("second.jsonl"));

        assertEquals(first, second);
        assertEquals("state", first.getFirst().required("type").textValue());
        assertTrue(first.getFirst().required("variables").has("function"));
        assertEquals("complete", first.getLast().required("type").textValue());
        assertEquals(1, first.getLast().required("states").intValue());
    }

    private List<JsonNode> runTlc(Path stateOutput) throws Exception {
        String javaExecutable =
                Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("tla.mutant.test.appJar")
                + System.getProperty("path.separator")
                + System.getProperty("tla.mutant.test.toolsJar");
        Process process = new ProcessBuilder(
                        javaExecutable,
                        "-Dtla.mutant.state.path=" + stateOutput.toAbsolutePath(),
                        "-Dtla.mutant.state.maxStates=100",
                        "-Dtla.mutant.state.maxBytes=1048576",
                        "-cp",
                        classpath,
                        "tlc2.TLC",
                        "-workers",
                        "1",
                        "-config",
                        "ValueShapes.cfg",
                        "-dump",
                        "class,dev.tlamutant.state.CanonicalStateWriter",
                        "ValueShapes.tla")
                .directory(Path.of("src/testFixtures/resources/tla/states").toFile())
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        String diagnostics =
                new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(completed, diagnostics);
        assertEquals(0, process.exitValue(), diagnostics);
        return Files.readAllLines(stateOutput).stream().map(this::parse).toList();
    }

    private JsonNode parse(String line) {
        try {
            return mapper.readTree(line);
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
