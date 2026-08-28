package dev.tlamutant.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.tlamutant.experiment.ExperimentReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonReportWriter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Path output, ExperimentReport report) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), ".report-", ".json");
        try {
            mapper.writeValue(temporary.toFile(), report);
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
