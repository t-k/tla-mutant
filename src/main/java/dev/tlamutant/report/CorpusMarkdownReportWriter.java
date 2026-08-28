package dev.tlamutant.report;

import dev.tlamutant.experiment.CorpusEntryResult;
import dev.tlamutant.experiment.CorpusExperimentReport;

public final class CorpusMarkdownReportWriter {
    public String render(CorpusExperimentReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# TLA+公開仕様のMutation縦切り実験\n\n")
                .append("候補抽出は自動proxy評価です。人間による有用性評価は別途必要です。\n\n")
                .append("- 固定revision: ")
                .append(report.commit())
                .append('\n')
                .append("- 仕様数: ")
                .append(report.entryCount())
                .append('\n')
                .append("- 完走仕様数: ")
                .append(report.completedEntries())
                .append('\n')
                .append("- 生成mutant数: ")
                .append(report.generatedMutants())
                .append('\n')
                .append("- 評価mutant数: ")
                .append(report.evaluatedMutants())
                .append('\n')
                .append("- State-Invariant Gap Candidate数: ")
                .append(report.gapCandidates())
                .append("\n\n")
                .append("## 仕様別結果\n\n")
                .append("| ID | ドメイン | 状態 | 生成 | 評価 | 候補 |\n")
                .append("| --- | --- | --- | ---: | ---: | ---: |\n");
        for (CorpusEntryResult entry : report.entries()) {
            markdown.append("| ")
                    .append(table(entry.id()))
                    .append(" | ")
                    .append(table(entry.domain()))
                    .append(" | ")
                    .append(table(entry.status()))
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

    private static String table(String value) {
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
