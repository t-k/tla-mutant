package dev.tlamutant.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class FrozenTextWriter {
    public void writeNew(Path output, String value) throws IOException {
        Objects.requireNonNull(value, "value");
        Path target = output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".frozen-text-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    value,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
