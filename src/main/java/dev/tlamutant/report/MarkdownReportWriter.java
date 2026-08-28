package dev.tlamutant.report;

import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.MutationResult;
import java.util.regex.Pattern;

public final class MarkdownReportWriter {
    private static final Pattern ANSI = Pattern.compile("\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])");
    private static final Pattern CONTROL_AND_BIDI =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\u202A-\\u202E\\u2066-\\u2069]");

    public String render(ExperimentReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# TLA Mutant Vertical Slice\n\n")
                .append("Automated proxy review; human requirement validation is pending.\n\n")
                .append("- Module: ")
                .append(safe(report.module()))
                .append('\n')
                .append("- Configuration: ")
                .append(safe(report.config()))
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
                    .append(safe(result.id()))
                    .append("\n\n")
                    .append("- Family: ")
                    .append(safe(result.family()))
                    .append('\n')
                    .append("- Definition: ")
                    .append(safe(result.definition()))
                    .append('\n')
                    .append("- Change: ")
                    .append(safe(result.original()))
                    .append(" -> ")
                    .append(safe(result.replacement()))
                    .append('\n')
                    .append("- Verdict: ")
                    .append(displayVerdict(result.verdict()))
                    .append('\n')
                    .append("- State relation: ")
                    .append(safe(result.stateRelation()))
                    .append('\n');
            if (result.mutantOnlyWitness() != null) {
                markdown.append("- Mutant-only witness: ")
                        .append(safe(result.mutantOnlyWitness()))
                        .append('\n');
            }
            markdown.append('\n');
        }
        return markdown.toString();
    }

    private static String displayVerdict(String verdict) {
        return verdict.equals("state-invariant-gap-candidate") ? "State-Invariant Gap Candidate" : safe(verdict);
    }

    private static String safe(String input) {
        String bounded = input == null ? "" : input.substring(0, Math.min(input.length(), 500));
        return CONTROL_AND_BIDI
                .matcher(ANSI.matcher(bounded).replaceAll(""))
                .replaceAll("")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("`", "'")
                .replace("\r", " ")
                .replace("\n", " ");
    }
}
