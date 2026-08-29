package dev.tlamutant.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FrozenJsonWriter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void writeNew(Path output, Object value) throws IOException {
        Path target = output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".frozen-json-", ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), value);
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
