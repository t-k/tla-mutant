package dev.tlamutant.corpus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DerivedModelWorkspaceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void insertsExactUtf8BytesIntoAPrivateCopyAndCleansIt() throws Exception {
        Path input = Files.createDirectory(temporaryDirectory.resolve("input"));
        byte[] original = "---- MODULE Spec ----\n====\n".getBytes(StandardCharsets.UTF_8);
        byte[] config = "SPECIFICATION Spec\n".getBytes(StandardCharsets.UTF_8);
        Files.write(input.resolve("Spec.tla"), original);
        Files.write(input.resolve("Spec.cfg"), config);
        String insertedText = "NoOverlap == TRUE\n";
        byte[] expected = insert(original, original.length - 5, insertedText.getBytes(StandardCharsets.UTF_8));
        CorpusManifest.DerivedSourceEdit edit = edit(original, original.length - 5, insertedText, expected);
        Path workspacePath = temporaryDirectory.resolve("workspace");

        try (DerivedModelWorkspace workspace = DerivedModelWorkspace.create(input, workspacePath, List.of(edit))) {
            assertEquals(workspacePath.toAbsolutePath().normalize(), workspace.root());
            assertArrayEquals(expected, Files.readAllBytes(workspace.root().resolve("Spec.tla")));
            assertArrayEquals(config, Files.readAllBytes(workspace.root().resolve("Spec.cfg")));
            assertArrayEquals(original, Files.readAllBytes(input.resolve("Spec.tla")));
        }

        assertFalse(Files.exists(workspacePath));
        assertArrayEquals(original, Files.readAllBytes(input.resolve("Spec.tla")));
    }

    @Test
    void rejectsBaseAndDerivedIdentityMismatchesWithoutLeavingAWorkspace() throws Exception {
        Path input = Files.createDirectory(temporaryDirectory.resolve("identity-input"));
        byte[] original = "abc".getBytes(StandardCharsets.UTF_8);
        Files.write(input.resolve("Spec.tla"), original);
        byte[] expected = "axbc".getBytes(StandardCharsets.UTF_8);
        CorpusManifest.DerivedSourceEdit valid = edit(original, 1, "x", expected);
        CorpusManifest.DerivedSourceEdit wrongBase = new CorpusManifest.DerivedSourceEdit(
                valid.path(),
                valid.definedOperator(),
                "0".repeat(64),
                valid.baseSizeBytes(),
                1,
                "x",
                valid.derivedSha256(),
                4);
        CorpusManifest.DerivedSourceEdit wrongDerived = new CorpusManifest.DerivedSourceEdit(
                valid.path(),
                valid.definedOperator(),
                valid.baseSha256(),
                valid.baseSizeBytes(),
                1,
                "x",
                "1".repeat(64),
                4);

        Path baseWorkspace = temporaryDirectory.resolve("base-workspace");
        Path derivedWorkspace = temporaryDirectory.resolve("derived-workspace");
        assertThrows(
                IllegalArgumentException.class,
                () -> DerivedModelWorkspace.create(input, baseWorkspace, List.of(wrongBase)));
        assertThrows(
                IllegalArgumentException.class,
                () -> DerivedModelWorkspace.create(input, derivedWorkspace, List.of(wrongDerived)));
        assertFalse(Files.exists(baseWorkspace));
        assertFalse(Files.exists(derivedWorkspace));
    }

    @Test
    void rejectsAnInsertionThatSplitsUtf8AndAPreexistingDestination() throws Exception {
        Path input = Files.createDirectory(temporaryDirectory.resolve("utf8-input"));
        byte[] original = "a日b".getBytes(StandardCharsets.UTF_8);
        Files.write(input.resolve("Spec.tla"), original);
        byte[] invalidExpected = insert(original, 2, "x".getBytes(StandardCharsets.UTF_8));
        CorpusManifest.DerivedSourceEdit split = edit(original, 2, "x", invalidExpected);

        assertThrows(
                IllegalArgumentException.class,
                () -> DerivedModelWorkspace.create(
                        input, temporaryDirectory.resolve("split-workspace"), List.of(split)));

        Path existing = Files.createDirectory(temporaryDirectory.resolve("existing"));
        assertThrows(IllegalArgumentException.class, () -> DerivedModelWorkspace.create(input, existing, List.of()));
        assertTrue(Files.isDirectory(existing));
    }

    @Test
    void rejectsForbiddenControlTextAtTheSandboxBoundary() throws Exception {
        Path input = Files.createDirectory(temporaryDirectory.resolve("control-input"));
        byte[] original = "abc".getBytes(StandardCharsets.UTF_8);
        Files.write(input.resolve("Spec.tla"), original);
        byte[] expected = "a\u0000bc".getBytes(StandardCharsets.UTF_8);
        CorpusManifest.DerivedSourceEdit edit = edit(original, 1, "\u0000", expected);

        assertThrows(
                IllegalArgumentException.class,
                () -> DerivedModelWorkspace.create(
                        input, temporaryDirectory.resolve("control-workspace"), List.of(edit)));
    }

    @Test
    void rejectsSymbolicLinksInsteadOfFollowingThem() throws Exception {
        Path input = Files.createDirectory(temporaryDirectory.resolve("link-input"));
        Files.writeString(input.resolve("Spec.tla"), "safe");
        Path external = Files.writeString(temporaryDirectory.resolve("external.cfg"), "secret");
        Files.createSymbolicLink(input.resolve("Spec.cfg"), external);

        assertThrows(
                IllegalArgumentException.class,
                () -> DerivedModelWorkspace.create(input, temporaryDirectory.resolve("link-workspace"), List.of()));
        assertFalse(Files.exists(temporaryDirectory.resolve("link-workspace")));
    }

    private static CorpusManifest.DerivedSourceEdit edit(
            byte[] original, int offset, String insertedText, byte[] expected) throws Exception {
        return new CorpusManifest.DerivedSourceEdit(
                "Spec.tla",
                "NoOverlap",
                digest(original),
                original.length,
                offset,
                insertedText,
                digest(expected),
                expected.length);
    }

    private static byte[] insert(byte[] original, int offset, byte[] inserted) {
        byte[] result = new byte[original.length + inserted.length];
        System.arraycopy(original, 0, result, 0, offset);
        System.arraycopy(inserted, 0, result, offset, inserted.length);
        System.arraycopy(original, offset, result, offset + inserted.length, original.length - offset);
        return result;
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
