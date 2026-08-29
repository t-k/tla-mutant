package dev.tlamutant.report;

import dev.tlamutant.prospective.ProspectiveAssessment;
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

    public String renderReview(ProspectiveAssessment assessment) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Prospective Invariant Review\n\n");
        markdown.append("Discovery status: `")
                .append(assessment.discoveryStatus())
                .append("`\n\n");
        markdown.append("Closure status: `").append(assessment.closureStatus()).append("`\n\n");
        markdown.append("| Measure | Count |\n|---|---:|\n");
        markdown.append("| Eligible | ").append(assessment.eligible()).append(" |\n");
        markdown.append("| Reviewed | ").append(assessment.reviewed()).append(" |\n");
        markdown.append("| Accepted | ").append(assessment.accepted()).append(" |\n");
        markdown.append("| Accepted beyond intervention cap | ")
                .append(assessment.acceptedOverflow())
                .append(" |\n\n");
        markdown.append(assessment.limitation()).append('\n');
        return markdown.toString();
    }
}
