package dev.tlamutant.corpus;

import dev.tlamutant.security.BoundedFileSnapshot;
import dev.tlamutant.security.SafeRelativePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Set;

public final class CorpusInputSnapshot implements AutoCloseable {
    private final Path root;

    private CorpusInputSnapshot(Path root) {
        this.root = root;
    }

    public static CorpusInputSnapshot create(Path verifiedRoot, CorpusManifest.Entry entry) throws IOException {
        Path sourceRoot = verifiedRoot.toAbsolutePath().normalize();
        Path snapshotRoot = Files.createTempDirectory("tla-mutant-corpus-");
        try {
            setPermissions(snapshotRoot, directoryPermissions());
            for (CorpusManifest.SourceFile file : entry.files()) {
                SafeRelativePath relative = SafeRelativePath.parse(file.path());
                Path source = relative.resolveRegularFile(sourceRoot);
                BoundedFileSnapshot snapshot = BoundedFileSnapshot.read(source, file.sizeBytes());
                if (snapshot.sizeBytes() != file.sizeBytes()
                        || !snapshot.sha256().equals(file.sha256())) {
                    throw new IllegalArgumentException("Corpus bytes changed after fetch verification");
                }
                Path destination = snapshotRoot.resolve(relative.value()).normalize();
                if (!destination.startsWith(snapshotRoot)) {
                    throw new IllegalArgumentException("Corpus snapshot path escapes its private root");
                }
                createPrivateDirectories(snapshotRoot, destination.getParent());
                Files.write(destination, snapshot.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                setPermissions(destination, Set.of(PosixFilePermission.OWNER_READ));
            }
            return new CorpusInputSnapshot(snapshotRoot);
        } catch (IOException | RuntimeException failure) {
            deleteTree(snapshotRoot);
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

    private static void createPrivateDirectories(Path root, Path destination) throws IOException {
        Path relative = root.relativize(destination);
        Path current = root;
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.notExists(current)) {
                Files.createDirectory(current);
                setPermissions(current, directoryPermissions());
            }
        }
    }

    private static Set<PosixFilePermission> directoryPermissions() {
        return Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // The platform's default private temporary-directory ACL remains in force.
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
