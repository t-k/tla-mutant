package dev.tlamutant.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackagedCensusCliIntegrationTest {
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(20);

    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedCensusWithoutToolsFailsBeforeInputHandlingWithActionableDiagnostic() throws Exception {
        Path work = temporaryDirectory.resolve("work");
        ProcessResult result = run(List.of(
                javaBinary(),
                "-jar",
                applicationJar(),
                "census",
                "--model-dir",
                "src/testFixtures/resources/tla/census",
                "--module",
                "CensusFixture.tla",
                "--config",
                "CensusFixture.cfg",
                "--work-dir",
                work.toString(),
                "--derived-source-edit-json",
                "not-json"));

        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("census requires TLA+ Tools on the JVM classpath"), result.output());
        assertTrue(result.output().contains("dev.tlamutant.cli.Main census"), result.output());
        assertFalse(result.output().contains("NoClassDefFoundError"), result.output());
        assertFalse(Files.exists(work));
    }

    @Test
    void documentedClasspathInvocationRunsRealCensus() throws Exception {
        String classpath = applicationJar() + System.getProperty("path.separator") + toolsJar();
        ProcessResult result = run(List.of(
                javaBinary(),
                "-cp",
                classpath,
                "dev.tlamutant.cli.Main",
                "census",
                "--model-dir",
                "src/testFixtures/resources/tla/census",
                "--module",
                "CensusFixture.tla",
                "--config",
                "CensusFixture.cfg"));

        assertEquals(0, result.exitCode(), result.output());
        JsonNode report = new ObjectMapper().readTree(result.output());
        assertEquals(1, report.required("schemaVersion").intValue());
        assertEquals(4, report.required("sites").size());
    }

    private ProcessResult run(List<String> command) throws Exception {
        Path output = Files.createTempFile(temporaryDirectory, "census-process-", ".log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            boolean completed = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertTrue(completed, () -> read(output));
            return new ProcessResult(process.exitValue(), read(output));
        } finally {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS), "census child did not terminate");
                }
            }
        }
    }

    private static String read(Path output) {
        try {
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static String applicationJar() {
        return System.getProperty("tla.mutant.test.appJar");
    }

    private static String toolsJar() {
        return System.getProperty("tla.mutant.test.toolsJar");
    }

    private record ProcessResult(int exitCode, String output) {}
}
