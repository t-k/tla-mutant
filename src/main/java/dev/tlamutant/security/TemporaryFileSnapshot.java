package dev.tlamutant.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class TemporaryFileSnapshot implements AutoCloseable {
    private final Path directory;
    private final Path path;

    private TemporaryFileSnapshot(Path directory, Path path) {
        this.directory = directory;
        this.path = path;
    }

    public static TemporaryFileSnapshot create(BoundedFileSnapshot snapshot, String fileName) throws IOException {
        if (fileName == null || !fileName.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalArgumentException("Temporary snapshot file name is invalid");
        }
        Path directory = Files.createTempDirectory("tla-mutant-snapshot-");
        try {
            setPermissions(
                    directory,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
            Path path = directory.resolve(fileName);
            Files.write(path, snapshot.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            setPermissions(path, Set.of(PosixFilePermission.OWNER_READ));
            return new TemporaryFileSnapshot(directory, path);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(directory.resolve(fileName));
            Files.deleteIfExists(directory);
            throw exception;
        }
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
        Files.deleteIfExists(directory);
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // The temporary path remains private under the platform's default ACLs.
        }
    }
}
