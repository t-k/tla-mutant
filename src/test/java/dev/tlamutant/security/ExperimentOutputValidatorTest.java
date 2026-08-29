package dev.tlamutant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExperimentOutputValidatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void returnsDistinctCanonicalOutputTargets() throws Exception {
        Path protectedFile = temporaryDirectory.resolve("census.json");
        Files.writeString(protectedFile, "frozen");
        Path cache = Files.createDirectory(temporaryDirectory.resolve("cache"));

        ExperimentOutputValidator.Outputs outputs = ExperimentOutputValidator.validate(
                temporaryDirectory.resolve("result.json"),
                temporaryDirectory.resolve("result.md"),
                List.of(protectedFile),
                List.of(cache));

        assertEquals(temporaryDirectory.toRealPath().resolve("result.json"), outputs.json());
        assertEquals(temporaryDirectory.toRealPath().resolve("result.md"), outputs.markdown());
    }

    @Test
    void rejectsOutputAliasesAndProtectedInputOrCacheTargets() throws Exception {
        Path protectedFile = temporaryDirectory.resolve("census.json");
        Files.writeString(protectedFile, "frozen");
        Path cache = Files.createDirectory(temporaryDirectory.resolve("cache"));
        Path result = temporaryDirectory.resolve("result.json");

        assertThrows(
                IllegalArgumentException.class,
                () -> ExperimentOutputValidator.validate(result, result, List.of(protectedFile), List.of(cache)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExperimentOutputValidator.validate(
                        protectedFile,
                        temporaryDirectory.resolve("report.md"),
                        List.of(protectedFile),
                        List.of(cache)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExperimentOutputValidator.validate(
                        cache.resolve("result.json"),
                        temporaryDirectory.resolve("report.md"),
                        List.of(protectedFile),
                        List.of(cache)));
    }
}
