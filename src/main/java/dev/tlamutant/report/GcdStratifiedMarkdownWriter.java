package dev.tlamutant.report;

import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.GcdCensusSite;
import dev.tlamutant.experiment.CorpusEntryResult;
import dev.tlamutant.experiment.GcdOutcomeSummary;
import dev.tlamutant.experiment.GcdStratifiedReport;
import dev.tlamutant.experiment.MutationResult;

public final class GcdStratifiedMarkdownWriter {
    public String render(GcdStratifiedReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# GCD-Stratified TLA+ Mutation Experiment\n\n")
                .append("The automated gate identifies witness-bearing candidates; it does not judge usefulness.\n\n")
                .append("- Profile: ")
                .append(MarkdownSanitizer.inline(report.profile()))
                .append('\n')
                .append("- Pinned revision: ")
                .append(MarkdownSanitizer.inline(report.commit()))
                .append('\n')
                .append("- Frozen census SHA-256: ")
                .append(MarkdownSanitizer.inline(report.censusSha256()))
                .append('\n')
                .append("- Frozen census bytes: ")
                .append(report.censusSizeBytes())
                .append('\n')
                .append("- Automated gate: ")
                .append(MarkdownSanitizer.inline(report.automatedGateStatus()))
                .append('\n')
                .append("- Human usefulness review: ")
                .append(MarkdownSanitizer.inline(report.humanReviewStatus()))
                .append("\n\n")
                .append("## Site-weighted summaries\n\n")
                .append(
                        "| Grouping | Value | Specifications | GCD sites | Killed, unattributed | Equal | Expanding | Incomparable | Restricting | Invalid | Unresolved | Witness-bearing |\n")
                .append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (GcdOutcomeSummary summary : report.summaries()) {
            markdown.append("| ")
                    .append(MarkdownSanitizer.tableCell(summary.grouping()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(summary.value()))
                    .append(" | ")
                    .append(summary.specifications())
                    .append(" | ")
                    .append(summary.totalSites())
                    .append(" | ")
                    .append(summary.killedUnattributed())
                    .append(" | ")
                    .append(summary.stateEqual())
                    .append(" | ")
                    .append(summary.stateExpanding())
                    .append(" | ")
                    .append(summary.stateIncomparable())
                    .append(" | ")
                    .append(summary.stateRestricting())
                    .append(" | ")
                    .append(summary.invalid())
                    .append(" | ")
                    .append(summary.unresolved())
                    .append(" | ")
                    .append(summary.witnessBearingCandidates())
                    .append(" |\n");
        }

        markdown.append("\n## Specification-weighted summaries\n\n")
                .append(
                        "Each outcome column counts specifications with at least one site in that outcome, so the columns may overlap.\n\n")
                .append(
                        "| Grouping | Value | Specifications | Killed, unattributed | Equal | Expanding | Incomparable | Restricting | Invalid | Unresolved | Witness-bearing |\n")
                .append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (GcdOutcomeSummary summary : report.summaries()) {
            markdown.append("| ")
                    .append(MarkdownSanitizer.tableCell(summary.grouping()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(summary.value()))
                    .append(" | ")
                    .append(summary.specifications())
                    .append(" | ")
                    .append(summary.specificationsKilledUnattributed())
                    .append(" | ")
                    .append(summary.specificationsStateEqual())
                    .append(" | ")
                    .append(summary.specificationsStateExpanding())
                    .append(" | ")
                    .append(summary.specificationsStateIncomparable())
                    .append(" | ")
                    .append(summary.specificationsStateRestricting())
                    .append(" | ")
                    .append(summary.specificationsInvalid())
                    .append(" | ")
                    .append(summary.specificationsUnresolved())
                    .append(" | ")
                    .append(summary.specificationsWithWitness())
                    .append(" |\n");
        }

        markdown.append("\n## GCD terminal outcomes\n\n")
                .append("Every frozen census site has exactly one terminal record.\n\n")
                .append("| Entry | Candidate | Status | Oracle | State relation | Verdict | Diagnostic |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (dev.tlamutant.experiment.GcdTerminalOutcome outcome : report.gcdOutcomes()) {
            MutationResult result = outcome.result();
            markdown.append("| ")
                    .append(MarkdownSanitizer.tableCell(outcome.entryId()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(result.id()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(result.executionStatus()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(result.oracleOutcome()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(result.stateRelation()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(result.verdict()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(
                            result.diagnosticReason() == null ? "-" : result.diagnosticReason()))
                    .append(" |\n");
        }

        markdown.append("\n## Non-GCD transparency totals\n\n")
                .append("| Family | Generated | Evaluated |\n")
                .append("| --- | ---: | ---: |\n")
                .append("| BRL | ")
                .append(report.brlGenerated())
                .append(" | ")
                .append(report.brlEvaluated())
                .append(" |\n| UOI | ")
                .append(report.uoiGenerated())
                .append(" | ")
                .append(report.uoiEvaluated())
                .append(" |\n\n")
                .append("## Preregistered census\n\n")
                .append("| Entry | Domain | Cohort | Stratum | Family cluster | Rationale | Status | Sites |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | ---: |\n");
        for (CorpusCensusEntryResult entry : report.census().entries()) {
            markdown.append("| ")
                    .append(MarkdownSanitizer.tableCell(entry.id()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(entry.domain()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(entry.selection().cohort()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(entry.selection().gcdStratum()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(entry.selection().familyCluster()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(entry.selection().selectionRationale()))
                    .append(" | ")
                    .append(MarkdownSanitizer.tableCell(entry.status()))
                    .append(" | ")
                    .append(
                            entry.report() == null
                                    ? "-"
                                    : entry.report().sites().size())
                    .append(" |\n");
        }

        markdown.append("\n### Census GCD sites\n\n")
                .append("| Entry | Candidate | Definition | Guard | State siblings | Action successors | Conforms |\n")
                .append("| --- | --- | --- | --- | ---: | ---: | --- |\n");
        for (CorpusCensusEntryResult entry : report.census().entries()) {
            if (entry.report() == null) {
                continue;
            }
            for (GcdCensusSite site : entry.report().sites()) {
                markdown.append("| ")
                        .append(MarkdownSanitizer.tableCell(entry.id()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(site.candidateId()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(site.definition()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(site.guard()))
                        .append(" | ")
                        .append(site.remainingStateConjuncts())
                        .append(" | ")
                        .append(site.actionSuccessorConjuncts())
                        .append(" | ")
                        .append(site.conforms())
                        .append(" |\n");
            }
        }

        appendDiagnostics(markdown, report);
        appendGapCandidates(markdown, report);
        return markdown.toString();
    }

    private static void appendDiagnostics(StringBuilder markdown, GcdStratifiedReport report) {
        markdown.append("\n## Incomplete and unresolved diagnostics\n\n");
        boolean found = false;
        for (CorpusCensusEntryResult entry : report.census().entries()) {
            if (entry.diagnostic() != null) {
                found = true;
                diagnostic(markdown, "census", entry.id(), entry.status(), entry.diagnostic());
            }
        }
        for (CorpusEntryResult entry : report.experiment().entries()) {
            if (entry.diagnostic() != null) {
                found = true;
                diagnostic(markdown, "experiment", entry.id(), entry.status(), entry.diagnostic());
            }
            if (entry.report() == null) {
                continue;
            }
            for (MutationResult result : entry.report().results()) {
                if (result.family().equals("GCD") && result.diagnosticReason() != null) {
                    found = true;
                    diagnostic(markdown, "GCD", result.id(), result.verdict(), result.diagnosticReason());
                }
            }
        }
        if (!found) {
            markdown.append("None.\n");
        }
    }

    private static void diagnostic(StringBuilder markdown, String source, String id, String status, String diagnostic) {
        markdown.append("- ")
                .append(MarkdownSanitizer.inline(source))
                .append(" / ")
                .append(MarkdownSanitizer.inline(id))
                .append(" / ")
                .append(MarkdownSanitizer.inline(status))
                .append(": ")
                .append(MarkdownSanitizer.inline(diagnostic))
                .append('\n');
    }

    private static void appendGapCandidates(StringBuilder markdown, GcdStratifiedReport report) {
        markdown.append("\n## Witness-bearing GCD gap candidates\n\n")
                .append("| Entry | Candidate | Definition | Original | Replacement | Relation | Witness |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (CorpusEntryResult entry : report.experiment().entries()) {
            if (entry.report() == null) {
                continue;
            }
            for (MutationResult result : entry.report().results()) {
                if (!result.family().equals("GCD")
                        || !result.verdict().equals("state-invariant-gap-candidate")
                        || result.mutantOnlyWitness() == null) {
                    continue;
                }
                markdown.append("| ")
                        .append(MarkdownSanitizer.tableCell(entry.id()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(result.id()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(result.definition()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(result.original()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(result.replacement()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(result.stateRelation()))
                        .append(" | ")
                        .append(MarkdownSanitizer.tableCell(result.mutantOnlyWitness()))
                        .append(" |\n");
            }
        }
    }
}
