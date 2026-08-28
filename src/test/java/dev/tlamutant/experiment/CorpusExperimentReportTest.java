package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CorpusExperimentReportTest {
    @Test
    void aggregatesOnlyCompletedEntryReports() {
        ExperimentReport completed =
                new ExperimentReport(1, "A.tla", "A.cfg", "proxy-agent-review", 3, 2, 1, List.of());

        CorpusExperimentReport report = CorpusExperimentReport.create(
                "https://github.com/tlaplus/Examples",
                "a".repeat(40),
                List.of(
                        new CorpusEntryResult("a", "puzzle", "COMPLETED", null, completed),
                        new CorpusEntryResult("b", "concurrency", "TIMEOUT", "bounded timeout", null)));

        assertEquals(2, report.entryCount());
        assertEquals(1, report.completedEntries());
        assertEquals(3, report.generatedMutants());
        assertEquals(2, report.evaluatedMutants());
        assertEquals(1, report.gapCandidates());
    }
}
