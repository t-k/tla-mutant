package dev.tlamutant.experiment;

import dev.tlamutant.security.BoundedFileSnapshot;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class FullPropertyModelSnapshot implements AutoCloseable {
    private static final int MAXIMUM_FILES = 256;
    private static final long MAXIMUM_FILE_BYTES = 64L * 1024 * 1024;
    private static final long MAXIMUM_TOTAL_BYTES = 128L * 1024 * 1024;

    private final Path root;
    private final List<Entry> entries;

    private FullPropertyModelSnapshot(Path root, List<Entry> entries) {
        this.root = root;
        this.entries = entries;
    }

    static FullPropertyModelSnapshot create(Path sourceDirectory) throws IOException {
        Path source = sourceDirectory.toAbsolutePath().normalize();
        BasicFileAttributes rootAttributes =
                Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!rootAttributes.isDirectory() || Files.isSymbolicLink(source)) {
            throw new IllegalArgumentException("Full-property model root must be a real directory");
        }
        List<Entry> entries = new ArrayList<>();
        long[] total = {0};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (Files.isSymbolicLink(directory)) {
                    throw new IllegalArgumentException("Full-property model cannot contain symbolic links");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
                    throw new IllegalArgumentException("Full-property model can contain only regular files");
                }
                if (entries.size() >= MAXIMUM_FILES) {
                    throw new IllegalArgumentException("Full-property model exceeds its file-count bound");
                }
                BoundedFileSnapshot snapshot = BoundedFileSnapshot.read(file, MAXIMUM_FILE_BYTES);
                total[0] = Math.addExact(total[0], snapshot.sizeBytes());
                if (total[0] > MAXIMUM_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Full-property model exceeds its aggregate byte bound");
                }
                String relative = source.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                entries.add(new Entry(relative, snapshot));
                return FileVisitResult.CONTINUE;
            }
        });
        entries.sort(Comparator.comparing(Entry::path));
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Full-property model must contain at least one file");
        }
        Path root = materialize(entries, "tla-mutant-full-property-");
        seal(root);
        return new FullPropertyModelSnapshot(root, List.copyOf(entries));
    }

    Path root() {
        return root;
    }

    List<ExperimentInputIdentity.ModelFile> files() {
        return entries.stream()
                .map(entry -> new ExperimentInputIdentity.ModelFile(
                        entry.path(), ExperimentInputIdentity.Artifact.from(entry.snapshot())))
                .toList();
    }

    Workspace createWorkspace() throws IOException {
        return new Workspace(materialize(entries, "tla-mutant-mutant-"));
    }

    @Override
    public void close() throws IOException {
        deleteTree(root);
    }

    private static Path materialize(List<Entry> entries, String prefix) throws IOException {
        Path destinationRoot = Files.createTempDirectory(prefix);
        try {
            for (Entry entry : entries) {
                Path destination = destinationRoot.resolve(entry.path()).normalize();
                if (!destination.startsWith(destinationRoot)) {
                    throw new IllegalArgumentException("Full-property model path escapes its private root");
                }
                Files.createDirectories(destination.getParent());
                Files.write(
                        destination, entry.snapshot().bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            return destinationRoot;
        } catch (IOException | RuntimeException exception) {
            deleteTree(destinationRoot);
            throw exception;
        }
    }

    private static void seal(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                setPermissions(
                        path,
                        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                                ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE)
                                : Set.of(PosixFilePermission.OWNER_READ));
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            List<Path> all = paths.toList();
            for (Path path : all) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    setPermissions(
                            path,
                            Set.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.OWNER_EXECUTE));
                }
            }
            for (Path path : all.stream().sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // The platform's private temporary-directory ACL remains in force.
        }
    }

    private record Entry(String path, BoundedFileSnapshot snapshot) {}

    static final class Workspace implements AutoCloseable {
        private final Path root;
        private boolean sealed;

        private Workspace(Path root) {
            this.root = root;
        }

        Path root() {
            return root;
        }

        void replace(String relativePath, String content) throws IOException {
            if (sealed) {
                throw new IllegalStateException("Full-property workspace is already sealed");
            }
            Path destination = root.resolve(relativePath).normalize();
            if (!destination.startsWith(root) || !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Mutation target is outside the frozen model");
            }
            Files.writeString(
                    destination,
                    content,
                    java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }

        void seal() throws IOException {
            FullPropertyModelSnapshot.seal(root);
            sealed = true;
        }

        @Override
        public void close() throws IOException {
            deleteTree(root);
        }
    }
}
