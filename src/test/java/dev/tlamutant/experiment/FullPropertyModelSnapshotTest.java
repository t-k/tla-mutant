package dev.tlamutant.experiment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FullPropertyModelSnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void freezesNestedFilesAndRecordsEveryExecutedIdentity() throws Exception {
        Path source = Files.createDirectories(temporaryDirectory.resolve("source/lib"));
        Files.writeString(source.getParent().resolve("Spec.tla"), "---- MODULE Spec ----\n====\n", UTF_8);
        Files.writeString(source.getParent().resolve("Spec.cfg"), "SPECIFICATION Spec\n", UTF_8);
        Files.writeString(source.resolve("Helper.tla"), "---- MODULE Helper ----\n====\n", UTF_8);
        Path root;

        try (FullPropertyModelSnapshot snapshot = FullPropertyModelSnapshot.create(source.getParent())) {
            root = snapshot.root();
            assertEquals(
                    java.util.List.of("Spec.cfg", "Spec.tla", "lib/Helper.tla"),
                    snapshot.files().stream()
                            .map(ExperimentInputIdentity.ModelFile::path)
                            .toList());
            assertEquals("---- MODULE Helper ----\n====\n", Files.readString(root.resolve("lib/Helper.tla")));
        }

        assertFalse(Files.exists(root));
    }

    @Test
    void rejectsSymbolicLinksAndAggregateOverflow() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("unsafe"));
        Path outside = temporaryDirectory.resolve("outside.tla");
        Files.writeString(outside, "outside", UTF_8);
        try {
            Files.createSymbolicLink(source.resolve("Linked.tla"), outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThrows(IllegalArgumentException.class, () -> FullPropertyModelSnapshot.create(source));
    }

    @Test
    void rejectsExcessiveDirectoryCountDepthAndPathLength() throws Exception {
        Path many = Files.createDirectory(temporaryDirectory.resolve("many"));
        for (int index = 0; index < 257; index++) {
            Files.createDirectory(many.resolve("d" + index));
        }
        Files.writeString(many.resolve("Spec.tla"), "---- MODULE Spec ----\n====\n", UTF_8);

        Path deep = Files.createDirectory(temporaryDirectory.resolve("deep"));
        Path cursor = deep;
        for (int index = 0; index < 17; index++) {
            cursor = Files.createDirectory(cursor.resolve("d"));
        }
        Files.writeString(cursor.resolve("Spec.tla"), "---- MODULE Spec ----\n====\n", UTF_8);

        Path longPath = Files.createDirectory(temporaryDirectory.resolve("long"));
        Path longDirectory = Files.createDirectory(longPath.resolve("x".repeat(241)));
        Files.writeString(longDirectory.resolve("Spec.tla"), "---- MODULE Spec ----\n====\n", UTF_8);

        assertThrows(IllegalArgumentException.class, () -> FullPropertyModelSnapshot.create(many));
        assertThrows(IllegalArgumentException.class, () -> FullPropertyModelSnapshot.create(deep));
        assertThrows(IllegalArgumentException.class, () -> FullPropertyModelSnapshot.create(longPath));
    }
}
