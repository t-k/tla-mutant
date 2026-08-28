package dev.tlamutant.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
