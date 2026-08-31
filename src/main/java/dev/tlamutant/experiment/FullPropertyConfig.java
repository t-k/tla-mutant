package dev.tlamutant.experiment;

import dev.tlamutant.security.BoundedFileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class FullPropertyConfig {
    private static final long MAXIMUM_CONFIG_BYTES = 8L * 1024 * 1024;

    private FullPropertyConfig() {}

    static Materialized write(Path source, Path target) throws IOException {
        BoundedFileSnapshot snapshot = BoundedFileSnapshot.read(source, MAXIMUM_CONFIG_BYTES);
        Path output = target.toAbsolutePath().normalize();
        Files.write(
                output,
                snapshot.bytes(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return new Materialized(output, snapshot);
    }

    record Materialized(Path path, BoundedFileSnapshot snapshot) {}
}
