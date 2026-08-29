package dev.tlamutant.corpus;

import dev.tlamutant.security.SafeRelativePath;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class DerivedModelWorkspace implements AutoCloseable {
    private final Path root;

    private DerivedModelWorkspace(Path root) {
        this.root = root;
    }

    public static DerivedModelWorkspace create(
            Path inputDirectory, Path workspaceDirectory, List<CorpusManifest.DerivedSourceEdit> edits)
            throws IOException {
        Path input = inputDirectory.toAbsolutePath().normalize();
        Path workspace = workspaceDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(input)) {
            throw new IllegalArgumentException("Derived model input must be a real directory");
        }
        if (Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Derived model workspace must not already exist");
        }
        List<CorpusManifest.DerivedSourceEdit> requested = DerivedSourceEditValidator.validateList(edits);
        try {
            Files.createDirectory(workspace);
            setPermissions(workspace, directoryPermissions());
            copyTree(input, workspace);
            for (CorpusManifest.DerivedSourceEdit edit : requested) {
                apply(workspace, edit);
            }
            return new DerivedModelWorkspace(workspace);
        } catch (IOException | RuntimeException failure) {
            deleteTree(workspace);
            throw failure;
        }
    }

    public Path root() {
        return root;
    }

    @Override
    public void close() throws IOException {
        deleteTree(root);
    }

    private static void copyTree(Path input, Path workspace) throws IOException {
        try (var paths = Files.walk(input)) {
            for (Path source : paths.sorted().toList()) {
                if (source.equals(input)) {
                    continue;
                }
                Path relative = input.relativize(source);
                Path destination = workspace.resolve(relative).normalize();
                if (!destination.startsWith(workspace)) {
                    throw new IllegalArgumentException("Derived model path escapes its private root");
                }
                if (Files.isSymbolicLink(source)) {
                    throw new IllegalArgumentException("Derived model input must not contain symbolic links");
                }
                if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectory(destination);
                    setPermissions(destination, directoryPermissions());
                } else if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                    Files.copy(source, destination);
                    setPermissions(destination, filePermissions());
                } else {
                    throw new IllegalArgumentException("Derived model input must contain only regular files");
                }
            }
        }
    }

    private static void apply(Path workspace, CorpusManifest.DerivedSourceEdit edit) throws IOException {
        Path target = SafeRelativePath.parse(edit.path()).resolveRegularFile(workspace);
        byte[] base = Files.readAllBytes(target);
        if (base.length != edit.baseSizeBytes() || !digest(base).equals(edit.baseSha256())) {
            throw new IllegalArgumentException("Derived source base identity mismatch");
        }
        int offset;
        try {
            offset = Math.toIntExact(edit.insertionOffset());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Derived source insertion offset is too large", exception);
        }
        requireUtf8Boundary(base, offset);
        byte[] inserted = edit.insertedText().getBytes(StandardCharsets.UTF_8);
        byte[] derived = new byte[Math.addExact(base.length, inserted.length)];
        System.arraycopy(base, 0, derived, 0, offset);
        System.arraycopy(inserted, 0, derived, offset, inserted.length);
        System.arraycopy(base, offset, derived, offset + inserted.length, base.length - offset);
        if (derived.length != edit.derivedSizeBytes() || !digest(derived).equals(edit.derivedSha256())) {
            throw new IllegalArgumentException("Derived source result identity mismatch");
        }
        Files.write(target, derived, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void requireUtf8Boundary(byte[] bytes, int offset) {
        try {
            var decoder = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes, 0, offset));
            decoder.reset();
            decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Derived source insertion must be on a UTF-8 boundary", exception);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Set<PosixFilePermission> directoryPermissions() {
        return Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    }

    private static Set<PosixFilePermission> filePermissions() {
        return Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // The platform's private directory defaults remain in force.
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
