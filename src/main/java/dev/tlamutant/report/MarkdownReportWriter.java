package dev.tlamutant.report;

import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.MutationResult;

public final class MarkdownReportWriter {
    public String render(ExperimentReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# TLA Mutant Vertical Slice\n\n")
                .append("Automated proxy review; human requirement validation is pending.\n\n")
                .append("- Module: ")
                .append(MarkdownSanitizer.inline(report.module()))
                .append('\n')
                .append("- Configuration: ")
                .append(MarkdownSanitizer.inline(report.config()))
                .append('\n')
                .append("- Generated mutants: ")
                .append(report.generatedMutants())
                .append('\n')
                .append("- Evaluated mutants: ")
                .append(report.evaluatedMutants())
                .append('\n')
                .append("- State-Invariant Gap Candidates: ")
                .append(report.gapCandidates())
                .append("\n\n")
                .append("## Results\n\n");
        for (MutationResult result : report.results()) {
            markdown.append("### ")
                    .append(MarkdownSanitizer.inline(result.id()))
                    .append("\n\n")
                    .append("- Family: ")
                    .append(MarkdownSanitizer.inline(result.family()))
                    .append('\n')
                    .append("- Definition: ")
                    .append(MarkdownSanitizer.inline(result.definition()))
                    .append('\n')
                    .append("- Change: ")
                    .append(MarkdownSanitizer.inline(result.original()))
                    .append(" -> ")
                    .append(MarkdownSanitizer.inline(result.replacement()))
                    .append('\n')
                    .append("- Verdict: ")
                    .append(displayVerdict(result.verdict()))
                    .append('\n')
                    .append("- State relation: ")
                    .append(MarkdownSanitizer.inline(result.stateRelation()))
                    .append('\n');
            if (result.diagnosticReason() != null) {
                markdown.append("- Diagnostic reason: ")
                        .append(MarkdownSanitizer.inline(result.diagnosticReason()))
                        .append('\n');
            }
            if (result.attribution() != null) {
                markdown.append("- Attribution: ")
                        .append(MarkdownSanitizer.inline(result.attribution().category()))
                        .append('\n');
                appendOracle(
                        markdown,
                        "Type-only oracle",
                        result.attribution().typeOnlyExecutionStatus(),
                        result.attribution().typeOnlyOracleOutcome());
                appendOracle(
                        markdown,
                        "Legacy oracle",
                        result.attribution().legacyExecutionStatus(),
                        result.attribution().legacyOracleOutcome());
                if (result.attribution().legacyStateRelation() != null) {
                    markdown.append("- Legacy state relation: ")
                            .append(MarkdownSanitizer.inline(
                                    result.attribution().legacyStateRelation()))
                            .append('\n');
                }
            }
            if (result.mutantOnlyWitness() != null) {
                markdown.append("- Mutant-only witness: ")
                        .append(MarkdownSanitizer.inline(result.mutantOnlyWitness()))
                        .append('\n');
            }
            markdown.append('\n');
        }
        return markdown.toString();
    }

    private static void appendOracle(StringBuilder markdown, String label, String execution, String oracle) {
        if (execution != null && oracle != null) {
            markdown.append("- ")
                    .append(label)
                    .append(": ")
                    .append(MarkdownSanitizer.inline(execution))
                    .append('/')
                    .append(MarkdownSanitizer.inline(oracle))
                    .append('\n');
        }
    }

    private static String displayVerdict(String verdict) {
        return verdict.equals("state-invariant-gap-candidate")
                ? "State-Invariant Gap Candidate"
                : MarkdownSanitizer.inline(verdict);
    }
}
