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
        CorpusManifest.Entry duplicate = entry(List.of(file("Spec.tla"), file("Spec.tla")));
        CorpusManifest.Entry caseCollision = entry(List.of(file("Spec.tla"), file("spec.tla")));

        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(duplicate, GLOBAL));
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(caseCollision, GLOBAL));
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
                new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90));
    }

    private static CorpusManifest.SourceFile file(String path) {
        return new CorpusManifest.SourceFile(path, "0".repeat(64), 1);
    }
}
