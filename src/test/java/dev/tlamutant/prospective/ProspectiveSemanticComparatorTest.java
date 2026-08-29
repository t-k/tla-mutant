package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tlamutant.experiment.CorpusEntryResult;
import dev.tlamutant.experiment.CorpusExperimentReport;
import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.MutationResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveSemanticComparatorTest {
    @Test
    void ignoresOnlyDiagnosticMetadata() {
        ProspectiveDiscoveryReportTest.Fixture fixture = ProspectiveDiscoveryReportTest.fixture();
        ProspectiveDiscoveryReport left = report(fixture, fixture.experiment());
        CorpusExperimentReport diagnosticDifference = replaceFirstResult(
                fixture.experiment(),
                withDiagnostic(firstResult(fixture.experiment()), "seed=99 pid=123 scratch=/tmp/other"));
        ProspectiveDiscoveryReport right = report(fixture, diagnosticDifference);

        ProspectiveSemanticComparator.Comparison comparison = new ProspectiveSemanticComparator().compare(left, right);

        assertTrue(comparison.equal(), comparison.firstDifference());
    }

    @Test
    void reportsTheFirstSemanticDifference() {
        ProspectiveDiscoveryReportTest.Fixture fixture = ProspectiveDiscoveryReportTest.fixture();
        ProspectiveDiscoveryReport left = report(fixture, fixture.experiment());
        MutationResult original = firstResult(fixture.experiment());
        MutationResult changed = new MutationResult(
                original.id(),
                original.family(),
                original.definition(),
                original.original(),
                original.replacement(),
                original.executionStatus(),
                original.oracleOutcome(),
                "STATE_EQUAL",
                original.verdict(),
                original.diagnosticReason(),
                original.baselineStateCount(),
                original.mutantStateCount(),
                original.mutantOnlyWitness(),
                original.attribution());
        ProspectiveDiscoveryReport right = report(fixture, replaceFirstResult(fixture.experiment(), changed));

        ProspectiveSemanticComparator.Comparison comparison = new ProspectiveSemanticComparator().compare(left, right);

        assertFalse(comparison.equal());
        assertTrue(comparison.firstDifference().contains("stateRelation"), comparison.firstDifference());
    }

    private static ProspectiveDiscoveryReport report(
            ProspectiveDiscoveryReportTest.Fixture fixture, CorpusExperimentReport experiment) {
        return ProspectiveDiscoveryReport.create(
                fixture.manifest(),
                fixture.census(),
                new dev.tlamutant.census.CensusInputIdentity.Artifact("b".repeat(64), 20),
                new dev.tlamutant.census.CensusInputIdentity.Artifact("a".repeat(64), 10),
                experiment);
    }

    private static MutationResult firstResult(CorpusExperimentReport report) {
        return report.entries().getFirst().report().results().getFirst();
    }

    private static MutationResult withDiagnostic(MutationResult result, String diagnostic) {
        return new MutationResult(
                result.id(),
                result.family(),
                result.definition(),
                result.original(),
                result.replacement(),
                result.executionStatus(),
                result.oracleOutcome(),
                result.stateRelation(),
                result.verdict(),
                diagnostic,
                result.baselineStateCount(),
                result.mutantStateCount(),
                result.mutantOnlyWitness(),
                result.attribution());
    }

    private static CorpusExperimentReport replaceFirstResult(
            CorpusExperimentReport report, MutationResult replacement) {
        List<CorpusEntryResult> entries = new ArrayList<>(report.entries());
        CorpusEntryResult first = entries.getFirst();
        List<MutationResult> results = new ArrayList<>(first.report().results());
        results.set(0, replacement);
        ExperimentReport original = first.report();
        ExperimentReport changedReport = new ExperimentReport(
                original.schemaVersion(),
                original.module(),
                original.config(),
                original.reviewStatus(),
                original.generatedMutants(),
                original.evaluatedMutants(),
                original.gapCandidates(),
                results);
        entries.set(0, new CorpusEntryResult(first.id(), first.domain(), "COMPLETED", null, changedReport));
        return CorpusExperimentReport.create(report.repository(), report.commit(), entries);
    }
}
