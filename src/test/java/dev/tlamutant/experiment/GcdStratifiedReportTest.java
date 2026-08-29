package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.CorpusCensusReport;
import dev.tlamutant.census.GcdCensusReport;
import dev.tlamutant.census.GcdCensusSite;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GcdStratifiedReportTest {
    private static final String REPOSITORY = "https://github.com/tlaplus/Examples";
    private static final String COMMIT = "a".repeat(40);

    @Test
    void keepsSpecificationAndSiteDenominatorsSeparate() {
        Fixture fixture = fixture(
                List.of(entry("first", "domain-a", "cluster-a"), entry("second", "domain-b", "cluster-b")),
                List.of(
                        List.of(
                                result(0, "STATE_EQUAL", "state-equal", null),
                                result(1, "STATE_EQUAL", "state-equal", null)),
                        List.of(result(2, "STATE_EQUAL", "state-equal", null))));

        GcdOutcomeSummary overall = fixture.report().summaries().stream()
                .filter(summary -> summary.grouping().equals("overall"))
                .findFirst()
                .orElseThrow();

        assertEquals(2, overall.specifications());
        assertEquals(3, overall.totalSites());
        assertEquals(
                overall.totalSites(),
                overall.killedUnattributed()
                        + overall.stateEqual()
                        + overall.stateExpanding()
                        + overall.stateIncomparable()
                        + overall.stateRestricting()
                        + overall.invalid()
                        + overall.unresolved());
    }

    @Test
    void conservesEveryGcdTerminalBucketAndKeepsBrlAndUoiSeparate() {
        List<MutationResult> results = new ArrayList<>(List.of(
                result(0, "STATE_UNRESOLVED", "state-invariant-kill-unattributed", null),
                result(1, "STATE_EQUAL", "state-equal", null),
                result(2, "STATE_EXPANDING", "state-invariant-gap-candidate", "witness-a"),
                result(3, "STATE_INCOMPARABLE", "state-invariant-gap-candidate", "witness-b"),
                result(4, "STATE_RESTRICTING", "state-restricting", null),
                invalid(5),
                unresolved(6),
                familyResult("brl-00000000000000000000", "BRL", "COMPLETED"),
                familyResult("uoi-00000000000000000000", "UOI", "TOOL_ERROR")));
        Fixture fixture = fixture(List.of(entry("all-buckets", "domain-a", "cluster-a")), List.of(results));
        GcdStratifiedReport report = fixture.report();
        GcdOutcomeSummary overall = report.summaries().get(0);

        assertEquals(1, overall.killedUnattributed());
        assertEquals(1, overall.stateEqual());
        assertEquals(1, overall.stateExpanding());
        assertEquals(1, overall.stateIncomparable());
        assertEquals(1, overall.stateRestricting());
        assertEquals(1, overall.invalid());
        assertEquals(1, overall.unresolved());
        assertEquals(1, report.brlGenerated());
        assertEquals(1, report.brlEvaluated());
        assertEquals(1, report.uoiGenerated());
        assertEquals(0, report.uoiEvaluated());
    }

    @Test
    void makesHumanReviewEligibleOnlyAcrossTwoSpecificationsDomainsAndClusters() {
        List<CorpusManifest.Entry> independent =
                List.of(entry("first", "domain-a", "cluster-a"), entry("second", "domain-b", "cluster-b"));
        Fixture eligible = fixture(
                independent,
                List.of(
                        List.of(result(0, "STATE_EXPANDING", "state-invariant-gap-candidate", "witness-a")),
                        List.of(result(1, "STATE_INCOMPARABLE", "state-invariant-gap-candidate", "witness-b"))));
        Fixture oneSpecification = fixture(
                List.of(entry("first", "domain-a", "cluster-a")),
                List.of(List.of(
                        result(0, "STATE_EXPANDING", "state-invariant-gap-candidate", "witness-a"),
                        result(1, "STATE_INCOMPARABLE", "state-invariant-gap-candidate", "witness-b"))));
        Fixture oneDomain = fixture(
                List.of(entry("first", "same-domain", "cluster-a"), entry("second", "same-domain", "cluster-b")),
                eligibleResults());
        Fixture oneCluster = fixture(
                List.of(entry("first", "domain-a", "same-cluster"), entry("second", "domain-b", "same-cluster")),
                eligibleResults());

        assertEquals("human-review-eligible", eligible.report().automatedGateStatus());
        assertEquals("pending", eligible.report().humanReviewStatus());
        assertEquals(
                "insufficient-witness-bearing-gcd-candidates",
                oneSpecification.report().automatedGateStatus());
        assertEquals(
                "insufficient-witness-bearing-gcd-candidates",
                oneDomain.report().automatedGateStatus());
        assertEquals(
                "insufficient-witness-bearing-gcd-candidates",
                oneCluster.report().automatedGateStatus());
    }

    @Test
    void rejectsCompletedExperimentWhoseGcdIdsDifferFromTheCensus() {
        List<CorpusManifest.Entry> entries = List.of(entry("first", "domain-a", "cluster-a"));
        Fixture fixture = fixture(entries, List.of(List.of(result(0, "STATE_EQUAL", "state-equal", null))));
        CorpusEntryResult original = fixture.experiment().entries().get(0);
        MutationResult replacement = result(99, "STATE_EQUAL", "state-equal", null);
        CorpusExperimentReport mismatch = CorpusExperimentReport.create(
                REPOSITORY,
                COMMIT,
                List.of(new CorpusEntryResult(
                        original.id(),
                        original.domain(),
                        "COMPLETED",
                        null,
                        experimentReport(entries.get(0), List.of(replacement)))));

        assertThrows(
                IllegalArgumentException.class,
                () -> GcdStratifiedReport.create(fixture.manifest(), fixture.census(), "b".repeat(64), 123, mismatch));
    }

    private static List<List<MutationResult>> eligibleResults() {
        return List.of(
                List.of(result(0, "STATE_EXPANDING", "state-invariant-gap-candidate", "witness-a")),
                List.of(result(1, "STATE_INCOMPARABLE", "state-invariant-gap-candidate", "witness-b")));
    }

    private static Fixture fixture(List<CorpusManifest.Entry> entries, List<List<MutationResult>> resultsByEntry) {
        CorpusManifest manifest = new CorpusManifest(2, "gcd-stratified-v1", REPOSITORY, COMMIT, "MIT", entries);
        List<CorpusCensusEntryResult> censusEntries = new ArrayList<>();
        List<CorpusEntryResult> experimentEntries = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            CorpusManifest.Entry entry = entries.get(index);
            List<GcdCensusSite> sites = resultsByEntry.get(index).stream()
                    .filter(result -> result.family().equals("GCD"))
                    .map(result -> site(result.id()))
                    .toList();
            censusEntries.add(new CorpusCensusEntryResult(
                    entry.id(),
                    entry.domain(),
                    entry.selection(),
                    "COMPLETED",
                    null,
                    new GcdCensusReport(1, entry.rootModule(), entry.config(), sites)));
            experimentEntries.add(new CorpusEntryResult(
                    entry.id(), entry.domain(), "COMPLETED", null, experimentReport(entry, resultsByEntry.get(index))));
        }
        CorpusCensusReport census = new CorpusCensusReport(1, manifest.profile(), REPOSITORY, COMMIT, censusEntries);
        CorpusExperimentReport experiment = CorpusExperimentReport.create(REPOSITORY, COMMIT, experimentEntries);
        GcdStratifiedReport report = GcdStratifiedReport.create(manifest, census, "b".repeat(64), 123, experiment);
        return new Fixture(manifest, census, experiment, report);
    }

    private static ExperimentReport experimentReport(CorpusManifest.Entry entry, List<MutationResult> results) {
        int evaluated = (int) results.stream()
                .filter(result -> result.executionStatus().equals("COMPLETED"))
                .count();
        int gaps = (int) results.stream()
                .filter(result -> result.verdict().equals("state-invariant-gap-candidate"))
                .count();
        return new ExperimentReport(
                1, entry.rootModule(), entry.config(), "proxy-agent-review", results.size(), evaluated, gaps, results);
    }

    private static CorpusManifest.Entry entry(String id, String domain, String cluster) {
        return new CorpusManifest.Entry(
                id,
                domain,
                "Spec.tla",
                "Spec.cfg",
                List.of(
                        new CorpusManifest.SourceFile("Spec.tla", "specifications/example/Spec.tla", "0".repeat(64), 1),
                        new CorpusManifest.SourceFile(
                                "Spec.cfg", "specifications/example/Spec.cfg", "1".repeat(64), 1)),
                List.of("TypeOK"),
                List.of("Safety"),
                new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90),
                new CorpusManifest.Selection(
                        "gcd-enriched", "lifecycle-or-pc-ordering", cluster, "source-shape rationale"));
    }

    private static GcdCensusSite site(String id) {
        return new GcdCensusSite(id, "Spec", "Next", "x > 0", 0, 0, 1, true);
    }

    private static MutationResult result(int index, String relation, String verdict, String witness) {
        return new MutationResult(
                "gcd-" + "%020x".formatted(index),
                "GCD",
                "Next",
                "x > 0",
                "TRUE",
                "COMPLETED",
                verdict.equals("state-invariant-kill-unattributed") ? "KILLED_STATE_SAFETY" : "PASSED",
                relation,
                verdict,
                verdict.equals("state-invariant-kill-unattributed") ? "type attribution not run" : null,
                1,
                2,
                witness);
    }

    private static MutationResult invalid(int index) {
        return new MutationResult(
                "gcd-" + "%020x".formatted(index),
                "GCD",
                "Next",
                "x > 0",
                "TRUE",
                "INVALID_SEMANTIC",
                "NOT_EVALUATED",
                "STATE_UNRESOLVED",
                "invalid",
                "post-mutation-sany-rejection",
                1,
                0,
                null);
    }

    private static MutationResult unresolved(int index) {
        return new MutationResult(
                "gcd-" + "%020x".formatted(index),
                "GCD",
                "Next",
                "x > 0",
                "TRUE",
                "TOOL_ERROR",
                "TOOL_ERROR",
                "STATE_UNRESOLVED",
                "unresolved",
                "tool-error",
                1,
                0,
                null);
    }

    private static MutationResult familyResult(String id, String family, String status) {
        return new MutationResult(
                id,
                family,
                "Next",
                "source",
                "replacement",
                status,
                status.equals("COMPLETED") ? "PASSED" : "TOOL_ERROR",
                status.equals("COMPLETED") ? "STATE_EQUAL" : "STATE_UNRESOLVED",
                status.equals("COMPLETED") ? "state-equal" : "unresolved",
                null,
                1,
                1,
                null);
    }

    private record Fixture(
            CorpusManifest manifest,
            CorpusCensusReport census,
            CorpusExperimentReport experiment,
            GcdStratifiedReport report) {}
}
