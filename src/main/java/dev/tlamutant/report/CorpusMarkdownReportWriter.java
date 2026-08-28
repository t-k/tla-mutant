package dev.tlamutant.report;

import dev.tlamutant.experiment.CorpusEntryResult;
import dev.tlamutant.experiment.CorpusExperimentReport;

public final class CorpusMarkdownReportWriter {
    public String render(CorpusExperimentReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# TLA+ Public-Specification Mutation Vertical Slice\n\n")
                .append(
                        "Candidate extraction is an automated proxy review. Human usefulness review is a separate gate.\n\n")
                .append("- Pinned revision: ")
                .append(MarkdownSanitizer.inline(report.commit()))
                .append('\n')
                .append("- Specifications: ")
                .append(report.entryCount())
                .append('\n')
                .append("- Completed specifications: ")
                .append(report.completedEntries())
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
                .append("## Results by specification\n\n")
                .append("| ID | Domain | Status | Generated | Evaluated | Candidates |\n")
                .append("| --- | --- | --- | ---: | ---: | ---: |\n");
        for (CorpusEntryResult entry : report.entries()) {
            markdown.append("| ")
                    .append(MarkdownSanitizer.tableCell(entry.id()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(entry.domain()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(entry.status()))
                    .append(" | ")
                    .append(entry.report() == null ? "-" : entry.report().generatedMutants())
                    .append(" | ")
                    .append(entry.report() == null ? "-" : entry.report().evaluatedMutants())
                    .append(" | ")
                    .append(entry.report() == null ? "-" : entry.report().gapCandidates())
                    .append(" |\n");
        }
        return markdown.toString();
    }
}
