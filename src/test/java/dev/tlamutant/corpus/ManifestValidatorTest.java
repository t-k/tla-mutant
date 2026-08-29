package dev.tlamutant.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.config.ExecutionPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ManifestValidatorTest {
    private static final ExecutionPolicy.Limits GLOBAL =
            new ExecutionPolicy.Limits(120, 1024, 64, 128, 4096, 100, 2048, 2048, 10_000, 180);

    @Test
    void acceptsLimitsThatOnlyTightenTheGlobalPolicy() {
        ExecutionPolicy.Limits requested = new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90);

        assertEquals(requested, ExecutionPolicy.enforceMaximums(GLOBAL, requested));
    }

    @Test
    void rejectsMissingNonPositiveOrIncreasedLimits() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionPolicy.enforceMaximums(GLOBAL, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExecutionPolicy.enforceMaximums(
                        GLOBAL, new ExecutionPolicy.Limits(121, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExecutionPolicy.enforceMaximums(
                        GLOBAL, new ExecutionPolicy.Limits(0, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90)));
    }

    @Test
    void rejectsDuplicateNormalizedAndCaseFoldedPaths() {
        CorpusManifest.Entry duplicate = entry(List.of(file("Spec.tla"), file("Spec.tla"), file("Spec.cfg")));
        CorpusManifest.Entry caseCollision = entry(List.of(file("Spec.tla"), file("spec.tla"), file("Spec.cfg")));

        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(duplicate, GLOBAL));
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(caseCollision, GLOBAL));
    }

    @Test
    void requiresRootAndConfigFilesAndSafeRepositoryPaths() {
        CorpusManifest.Entry missingConfig = entry(List.of(file("Spec.tla")));
        CorpusManifest.Entry unsafeRepositoryPath = entry(
                List.of(new CorpusManifest.SourceFile("Spec.tla", "../Spec.tla", "0".repeat(64), 1), file("Spec.cfg")));

        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(missingConfig, GLOBAL));
        assertThrows(
                IllegalArgumentException.class, () -> ManifestValidator.validateEntry(unsafeRepositoryPath, GLOBAL));
    }

    @Test
    void rejectsAnUnboundedSourceFileSetOrByteTotal() {
        List<CorpusManifest.SourceFile> tooMany = new java.util.ArrayList<>();
        for (int index = 0; index < 65; index++) {
            tooMany.add(file("Source" + index + ".tla"));
        }
        CorpusManifest.Entry tooLarge = entry(List.of(
                new CorpusManifest.SourceFile(
                        "Spec.tla", "specifications/sample/Spec.tla", "0".repeat(64), 20L * 1024 * 1024),
                new CorpusManifest.SourceFile(
                        "Spec.cfg", "specifications/sample/Spec.cfg", "0".repeat(64), 20L * 1024 * 1024)));

        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(entry(tooMany), GLOBAL));
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(tooLarge, GLOBAL));
    }

    private static CorpusManifest.Entry entry(List<CorpusManifest.SourceFile> files) {
        return new CorpusManifest.Entry(
                "sample",
                "distributed",
                "Spec.tla",
                "Spec.cfg",
                files,
                List.of("TypeOK"),
                List.of("Safety"),
                new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90),
                null);
    }

    private static CorpusManifest.SourceFile file(String path) {
        return new CorpusManifest.SourceFile(path, "specifications/sample/" + path, "0".repeat(64), 1);
    }
}
