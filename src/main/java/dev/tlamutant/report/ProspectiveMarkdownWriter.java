package dev.tlamutant.report;

import dev.tlamutant.prospective.ProspectiveDiscoveryReport;

public final class ProspectiveMarkdownWriter {
    public String renderDiscovery(ProspectiveDiscoveryReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Prospective Invariant Discovery\n\n");
        markdown.append("Profile: `").append(report.profile()).append("`\n\n");
        markdown.append("## Conserving Summary\n\n");
        markdown.append("| Measure | Count |\n|---|---:|\n");
        markdown.append("| Frozen specifications | ")
                .append(report.summary().specifications())
                .append(" |\n");
        markdown.append("| Census-censored specifications | ")
                .append(report.summary().censusCensoredSpecifications())
                .append(" |\n");
        markdown.append("| Experiment-censored specifications | ")
                .append(report.summary().experimentCensoredSpecifications())
                .append(" |\n");
        markdown.append("| Frozen GCD sites | ")
                .append(report.summary().censusSites())
                .append(" |\n");
        markdown.append("| Eligible candidates | ")
                .append(report.summary().eligibleCandidates())
                .append(" |\n\n");
        markdown.append(
                "Agent review is user-delegated and finite-model evidence does not establish an unbounded proof.\n");
        return markdown.toString();
    }
}
