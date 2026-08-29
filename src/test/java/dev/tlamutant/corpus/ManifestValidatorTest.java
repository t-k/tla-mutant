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

    @Test
    void rejectsUnsafeDuplicateOrOverlappingInvariantNames() {
        CorpusManifest.Entry unsafe = withInvariants(List.of("TypeOK\nINVARIANT Injected"), List.of("Safety"));
        CorpusManifest.Entry duplicate = withInvariants(List.of("TypeOK", "TypeOK"), List.of("Safety"));
        CorpusManifest.Entry overlap = withInvariants(List.of("TypeOK"), List.of("TypeOK"));

        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(unsafe, GLOBAL));
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(duplicate, GLOBAL));
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(overlap, GLOBAL));
    }

    @Test
    void acceptsOneBoundedDigestPinnedInsertionAndAdoptedTarget() {
        CorpusManifest.Entry original = entry(List.of(file("Spec.tla"), file("Spec.cfg")));
        CorpusManifest.Entry edited = withV9Fields(
                original,
                List.of("NoOverlap"),
                List.of(new CorpusManifest.DerivedSourceEdit(
                        "Spec.tla", "NoOverlap", "0".repeat(64), 1, 1, "\nNoOverlap == TRUE\n", "1".repeat(64), 20)));

        assertEquals(edited, ManifestValidator.validateEntry(edited, GLOBAL));
    }

    @Test
    void rejectsInvalidDerivedSourceEditMetadata() {
        CorpusManifest.Entry original = entry(List.of(file("Spec.tla"), file("Spec.cfg")));
        List<CorpusManifest.DerivedSourceEdit> invalid = List.of(
                edit("Missing.tla", "0".repeat(64), 1, 1, "x", "1".repeat(64), 2),
                edit("Spec.cfg", "0".repeat(64), 1, 1, "x", "1".repeat(64), 2),
                edit("Spec.tla", "2".repeat(64), 1, 1, "x", "1".repeat(64), 2),
                edit("Spec.tla", "0".repeat(64), 2, 1, "x", "1".repeat(64), 3),
                edit("Spec.tla", "0".repeat(64), 1, -1, "x", "1".repeat(64), 2),
                edit("Spec.tla", "0".repeat(64), 1, 2, "x", "1".repeat(64), 2),
                edit("Spec.tla", "0".repeat(64), 1, 1, "", "1".repeat(64), 1),
                edit("Spec.tla", "0".repeat(64), 1, 1, "x\u0000", "1".repeat(64), 3),
                edit("Spec.tla", "0".repeat(64), 1, 1, "x".repeat(4097), "1".repeat(64), 4098),
                edit("Spec.tla", "0".repeat(64), 1, 1, "x", "bad", 2),
                edit("Spec.tla", "0".repeat(64), 1, 1, "x", "1".repeat(64), 3));

        for (CorpusManifest.DerivedSourceEdit edit : invalid) {
            CorpusManifest.Entry entry = withV9Fields(original, List.of("NoOverlap"), List.of(edit));
            assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(entry, GLOBAL));
        }

        CorpusManifest.Entry duplicateEdits = withV9Fields(
                original,
                List.of("NoOverlap"),
                List.of(
                        edit("Spec.tla", "0".repeat(64), 1, 1, "x", "1".repeat(64), 2),
                        edit("Spec.tla", "0".repeat(64), 1, 1, "y", "2".repeat(64), 2)));
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(duplicateEdits, GLOBAL));
    }

    @Test
    void rejectsAdoptedInvariantOverlapWithExistingCategories() {
        CorpusManifest.Entry original = entry(List.of(file("Spec.tla"), file("Spec.cfg")));

        CorpusManifest.Entry overlap = withV9Fields(original, List.of("Safety"), List.of());

        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.validateEntry(overlap, GLOBAL));
    }

    private static CorpusManifest.Entry withInvariants(List<String> type, List<String> target) {
        CorpusManifest.Entry entry = entry(List.of(file("Spec.tla"), file("Spec.cfg")));
        return new CorpusManifest.Entry(
                entry.id(),
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                entry.files(),
                type,
                target,
                entry.limits(),
                entry.selection());
    }

    private static CorpusManifest.Entry withV9Fields(
            CorpusManifest.Entry entry,
            List<String> adoptedTargetInvariants,
            List<CorpusManifest.DerivedSourceEdit> edits) {
        return new CorpusManifest.Entry(
                entry.id(),
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                entry.files(),
                entry.typeInvariants(),
                entry.targetInvariants(),
                adoptedTargetInvariants,
                edits,
                entry.limits(),
                entry.selection());
    }

    private static CorpusManifest.DerivedSourceEdit edit(
            String path,
            String baseSha256,
            long baseSizeBytes,
            long insertionOffset,
            String insertedText,
            String derivedSha256,
            long derivedSizeBytes) {
        return new CorpusManifest.DerivedSourceEdit(
                path,
                "NoOverlap",
                baseSha256,
                baseSizeBytes,
                insertionOffset,
                insertedText,
                derivedSha256,
                derivedSizeBytes);
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
