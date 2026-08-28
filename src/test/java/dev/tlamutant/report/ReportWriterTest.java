package dev.tlamutant.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tlamutant.experiment.CorpusEntryResult;
import dev.tlamutant.experiment.CorpusExperimentReport;
import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.MutationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ReportWriterTest {
    @Test
    void markdownUsesBoundedCandidateTerminologyAndNeutralizesHostileText() {
        MutationResult result = new MutationResult(
                "gcd-1",
                "GCD",
                "Next",
                "x < 3\u001b[31m</script>```",
                "TRUE",
                "COMPLETED",
                "PASSED",
                "STATE_EXPANDING",
                "state-invariant-gap-candidate",
                null,
                1,
                2,
                "1:x:9:integer:1");
        ExperimentReport report =
                new ExperimentReport(1, "Fixture.tla", "Fixture.cfg", "proxy-agent-review", 1, 1, 1, List.of(result));

        String markdown = new MarkdownReportWriter().render(report);

        assertTrue(markdown.contains("State-Invariant Gap Candidate"));
        assertTrue(markdown.contains("Automated proxy review"));
        assertFalse(markdown.contains("\u001b"));
        assertFalse(markdown.contains("</script>"));
        assertFalse(markdown.contains("```"));
    }

    @Test
    void corpusMarkdownNeutralizesLinksImagesHtmlControlsAndTableDelimiters() {
        CorpusEntryResult entry = new CorpusEntryResult(
                "![image](javascript:alert(1))|next\u202e",
                "<img src=x onerror=alert(1)>\u0000\u061c\u200e\u200f\u0085",
                "COMPLETED",
                null,
                new ExperimentReport(1, "Fixture.tla", "Fixture.cfg", "proxy-agent-review", 0, 0, 0, List.of()));
        CorpusExperimentReport report = CorpusExperimentReport.create(
                "https://example.invalid/[repository](javascript:alert(1))", "revision|injected\nrow", List.of(entry));

        String markdown = new CorpusMarkdownReportWriter().render(report);

        assertFalse(markdown.contains("![image]"));
        assertFalse(markdown.contains("](javascript:"));
        assertFalse(markdown.contains("<img"));
        assertFalse(markdown.contains("\u202e"));
        assertFalse(markdown.contains("\u0000"));
        assertFalse(markdown.contains("\u061c"));
        assertFalse(markdown.contains("\u200e"));
        assertFalse(markdown.contains("\u200f"));
        assertFalse(markdown.contains("\u0085"));
        assertTrue(markdown.contains("\\|"));
        assertFalse(markdown.contains("injected\nrow"));
    }
}
