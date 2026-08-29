package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.CorpusCensusReport;
import dev.tlamutant.census.GcdCensusReport;
import dev.tlamutant.census.GcdCensusSite;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.experiment.CorpusEntryResult;
import dev.tlamutant.experiment.CorpusExperimentReport;
import dev.tlamutant.experiment.ExperimentReport;
import dev.tlamutant.experiment.MutationResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveDiscoveryReportTest {
    private static final CensusInputIdentity.Artifact RECEIPT = new CensusInputIdentity.Artifact("a".repeat(64), 10);
    private static final CensusInputIdentity.Artifact CENSUS_ARTIFACT =
            new CensusInputIdentity.Artifact("b".repeat(64), 20);

    @Test
    void terminalizesEveryFrozenSiteAndRetainsEverySpecification() {
        Fixture fixture = fixture();

        ProspectiveDiscoveryReport report = ProspectiveDiscoveryReport.create(
                fixture.manifest(), fixture.census(), CENSUS_ARTIFACT, RECEIPT, fixture.experiment());

        assertEquals(8, report.summary().specifications());
        assertEquals(7, report.summary().censusSites());
        assertEquals(7, report.gcdOutcomes().size());
        assertEquals(8, report.specifications().size());
        assertEquals(1, report.summary().censusCensoredSpecifications());
        assertEquals(1, report.summary().eligibleCandidates());
        assertEquals("CENSUS_CENSORED", report.specifications().get(2).status());
    }

    @Test
    void rejectsMissingOrInventedGcdResults() {
        Fixture fixture = fixture();
        List<CorpusEntryResult> entries = new ArrayList<>(fixture.experiment().entries());
        CorpusEntryResult first = entries.getFirst();
        List<MutationResult> missing = first.report().results().subList(0, 1);
        entries.set(0, completed(fixture.manifest().entries().getFirst(), missing));
        CorpusExperimentReport changed = CorpusExperimentReport.create(
                fixture.manifest().repository(), fixture.manifest().commit(), entries);

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveDiscoveryReport.create(
                        fixture.manifest(), fixture.census(), CENSUS_ARTIFACT, RECEIPT, changed));
    }

    @Test
    void toolErrorsRemainUnresolvedAndNeverBecomeEligible() {
        Fixture fixture = fixture();
        List<CorpusEntryResult> entries = new ArrayList<>(fixture.experiment().entries());
        CorpusManifest.Entry fourth = fixture.manifest().entries().get(3);
        entries.set(3, new CorpusEntryResult(fourth.id(), fourth.domain(), "TIMEOUT", "bounded timeout", null));
        CorpusExperimentReport changed = CorpusExperimentReport.create(
                fixture.manifest().repository(), fixture.manifest().commit(), entries);

        ProspectiveDiscoveryReport report = ProspectiveDiscoveryReport.create(
                fixture.manifest(), fixture.census(), CENSUS_ARTIFACT, RECEIPT, changed);

        assertEquals(1, report.summary().experimentCensoredSpecifications());
        assertEquals(
                "BASELINE_UNRESOLVED",
                report.gcdOutcomes().stream()
                        .filter(outcome -> outcome.entryId().equals(fourth.id()))
                        .findFirst()
                        .orElseThrow()
                        .result()
                        .executionStatus());
        assertEquals(1, report.summary().eligibleCandidates());
    }

    static Fixture fixture() {
        CorpusManifest manifest = manifest();
        CensusInputIdentity inputs = new CensusInputIdentity(
                new CensusInputIdentity.Artifact("c".repeat(64), 1),
                new CensusInputIdentity.Artifact("d".repeat(64), 1),
                new CensusInputIdentity.Artifact("e".repeat(64), 1),
                new CensusInputIdentity.Artifact("f".repeat(64), 1));
        List<CorpusCensusEntryResult> censusEntries = new ArrayList<>();
        censusEntries.add(censusCompleted(manifest.entries().get(0), List.of(site(0), site(1))));
        censusEntries.add(censusCompleted(manifest.entries().get(1), List.of()));
        CorpusManifest.Entry failed = manifest.entries().get(2);
        censusEntries.add(new CorpusCensusEntryResult(
                failed.id(), failed.domain(), failed.selection(), "TIMEOUT", "bounded timeout", null));
        for (int index = 3; index < 8; index++) {
            censusEntries.add(censusCompleted(manifest.entries().get(index), List.of(site(index))));
        }
        ProspectiveCensusReport census = new ProspectiveCensusReport(
                1,
                RECEIPT,
                new CorpusCensusReport(
                        3, manifest.profile(), manifest.repository(), manifest.commit(), inputs, censusEntries));

        List<CorpusEntryResult> experimentEntries = new ArrayList<>();
        experimentEntries.add(completed(
                manifest.entries().get(0),
                List.of(
                        result(0, "STATE_EXPANDING", "state-invariant-gap-candidate", "{x |-> 2}"),
                        result(1, "STATE_EQUAL", "survived", null))));
        experimentEntries.add(completed(manifest.entries().get(1), List.of()));
        experimentEntries.add(new CorpusEntryResult(
                failed.id(), failed.domain(), "CENSUS_UNRESOLVED", "Census status: TIMEOUT", null));
        for (int index = 3; index < 8; index++) {
            experimentEntries.add(completed(
                    manifest.entries().get(index), List.of(result(index, "STATE_RESTRICTING", "survived", null))));
        }
        return new Fixture(
                manifest,
                census,
                CorpusExperimentReport.create(manifest.repository(), manifest.commit(), experimentEntries));
    }

    private static CorpusEntryResult completed(CorpusManifest.Entry entry, List<MutationResult> results) {
        return new CorpusEntryResult(
                entry.id(),
                entry.domain(),
                "COMPLETED",
                null,
                new ExperimentReport(
                        2,
                        entry.rootModule(),
                        entry.config(),
                        "automated",
                        results.size(),
                        results.size(),
                        (int) results.stream()
                                .filter(result -> result.verdict().equals("state-invariant-gap-candidate"))
                                .count(),
                        results));
    }

    private static MutationResult result(int index, String relation, String verdict, String witness) {
        return new MutationResult(
                "gcd-" + "%020x".formatted(index),
                "GCD",
                "Next",
                "x > 0",
                "TRUE",
                "COMPLETED",
                "PASSED",
                relation,
                verdict,
                "diagnostic seed=1 scratch=/tmp/a",
                1,
                relation.equals("STATE_EXPANDING") ? 2 : 1,
                witness);
    }

    private static CorpusCensusEntryResult censusCompleted(CorpusManifest.Entry entry, List<GcdCensusSite> sites) {
        return new CorpusCensusEntryResult(
                entry.id(),
                entry.domain(),
                entry.selection(),
                "COMPLETED",
                null,
                new GcdCensusReport(1, entry.rootModule(), entry.config(), sites));
    }

    private static GcdCensusSite site(int index) {
        return new GcdCensusSite("gcd-" + "%020x".formatted(index), "Spec", "Next", "x > 0", 0, 1, 1, true);
    }

    private static CorpusManifest manifest() {
        List<CorpusManifest.Entry> entries = new ArrayList<>();
        ExecutionPolicy.Limits limits = new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90);
        for (int index = 0; index < 8; index++) {
            String root = "Spec" + index + ".tla";
            String config = "Spec" + index + ".cfg";
            String risk = index < 6 ? ProspectiveProtocol.STANDARD_RISK : ProspectiveProtocol.HIGH_RISK;
            entries.add(new CorpusManifest.Entry(
                    "entry-" + index,
                    "domain-" + index,
                    root,
                    config,
                    List.of(
                            new CorpusManifest.SourceFile(root, "prospective/" + root, "a".repeat(64), 1),
                            new CorpusManifest.SourceFile(config, "prospective/" + config, "b".repeat(64), 1)),
                    List.of("TypeOK"),
                    List.of("Safety"),
                    limits,
                    new CorpusManifest.Selection("prospective", risk, "family-" + index, "Frozen candidate.")));
        }
        return new CorpusManifest(
                4, "gcd-prospective-v10", "https://github.com/tlaplus/Examples", "c".repeat(40), "MIT", entries);
    }

    record Fixture(CorpusManifest manifest, ProspectiveCensusReport census, CorpusExperimentReport experiment) {}
}
