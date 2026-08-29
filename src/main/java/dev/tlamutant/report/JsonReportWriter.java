package dev.tlamutant.report;

import dev.tlamutant.experiment.ExperimentReport;
import java.io.IOException;
import java.nio.file.Path;

public final class JsonReportWriter {
    public void write(Path output, ExperimentReport report) throws IOException {
        new AtomicJsonWriter().write(output, report);
    }
}
