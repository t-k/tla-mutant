package dev.tlamutant.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.CorpusCensusReport;
import dev.tlamutant.census.GcdCensusReport;
import dev.tlamutant.census.GcdCensusSite;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.experiment.CorpusEntryResult;
import dev.tlamutant.experiment.CorpusExperimentReport;
import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.GcdOutcomeSummary;
import dev.tlamutant.experiment.GcdStratifiedReport;
import dev.tlamutant.experiment.KillAttribution;
import dev.tlamutant.experiment.MutationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ReportWriterTest {
    @Test
    void legacyJsonOmitsAdditiveFullPropertyFields() throws Exception {
        MutationResult result = new MutationResult(
                "gcd-1",
                "GCD",
                "Next",
                "guard",
                "TRUE",
                "COMPLETED",
                "PASSED",
                "STATE_EQUAL",
                "state-equal",
                null,
                1,
                1,
                null);
        ExperimentReport report = new ExperimentReport(1, "Spec.tla", "Spec.cfg", "proxy", 1, 1, 0, List.of(result));

        String json = new ObjectMapper().writeValueAsString(report);

        assertFalse(json.contains("oracleMode"));
        assertFalse(json.contains("inputs"));
        assertFalse(json.contains("evidence"));
        assertTrue(json.contains("\"diagnosticReason\":null"));
    }

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

    @Test
    void individualMarkdownRendersStructuredAttributionEvidence() {
        KillAttribution attribution = new KillAttribution(
                "adopted-target-invariant-kill",
                "COMPLETED",
                "PASSED",
                4,
                "COMPLETED",
                "PASSED",
                "STATE_EXPANDING",
                8,
                "1:x:2:integer:2",
                null);
        MutationResult result = new MutationResult(
                "gcd-1",
                "GCD",
                "Request",
                "guard",
                "TRUE",
                "COMPLETED",
                "KILLED_STATE_SAFETY",
                "STATE_UNRESOLVED",
                attribution.category(),
                null,
                4,
                5,
                null,
                attribution);

        String markdown = new MarkdownReportWriter()
                .render(new ExperimentReport(2, "Spec.tla", "Spec.cfg", "proxy", 1, 1, 0, List.of(result)));

        assertTrue(markdown.contains("Attribution: adopted-target-invariant-kill"));
        assertTrue(markdown.contains("Type-only oracle: COMPLETED/PASSED"));
        assertTrue(markdown.contains("Legacy oracle: COMPLETED/PASSED"));
        assertTrue(markdown.contains("Legacy state relation: STATE_EXPANDING"));
    }

    @Test
    void stratifiedMarkdownNeutralizesCensusSelectionDiagnosticAndWitnessText() {
        String hostile = "![image](javascript:alert(1))|<script>\u001b[31m\u202e\nrow";
        CorpusManifest.Selection selection =
                new CorpusManifest.Selection("gcd-enriched", "lifecycle", hostile, hostile);
        GcdCensusSite site = new GcdCensusSite("gcd-00000000000000000000", "Spec", "Next", hostile, 0, 0, 1, true);
        CorpusCensusReport census = new CorpusCensusReport(
                1,
                "gcd-stratified-v1",
                "https://github.com/tlaplus/Examples",
                "a".repeat(40),
                List.of(new CorpusCensusEntryResult(
                        "entry",
                        "domain",
                        selection,
                        "COMPLETED",
                        null,
                        new GcdCensusReport(1, "Spec.tla", "Spec.cfg", List.of(site)))));
        MutationResult mutation = new MutationResult(
                site.candidateId(),
                "GCD",
                hostile,
                hostile,
                "TRUE",
                "COMPLETED",
                "PASSED",
                "STATE_EXPANDING",
                "state-invariant-gap-candidate",
                hostile,
                1,
                2,
                hostile);
        CorpusExperimentReport experiment = CorpusExperimentReport.create(
                census.repository(),
                census.commit(),
                List.of(new CorpusEntryResult(
                        "entry",
                        "domain",
                        "COMPLETED",
                        null,
                        new ExperimentReport(1, "Spec.tla", "Spec.cfg", "proxy", 1, 1, 1, List.of(mutation)))));
        GcdOutcomeSummary summary =
                new GcdOutcomeSummary("overall", "all", 1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 1);
        GcdStratifiedReport report = new GcdStratifiedReport(
                1,
                census.profile(),
                census.repository(),
                census.commit(),
                "b".repeat(64),
                123,
                "human-review-eligible",
                "pending",
                1,
                1,
                0,
                0,
                0,
                0,
                List.of(summary),
                List.of(),
                census,
                experiment);

        String markdown = new GcdStratifiedMarkdownWriter().render(report);

        assertTrue(markdown.contains("Human usefulness review: pending"));
        assertTrue(markdown.contains("gcd-00000000000000000000"));
        assertFalse(markdown.contains("![image]"));
        assertFalse(markdown.contains("](javascript:"));
        assertFalse(markdown.contains("<script>"));
        assertFalse(markdown.contains("\u001b"));
        assertFalse(markdown.contains("\u202e"));
        assertFalse(markdown.contains("\nrow"));
    }
}
