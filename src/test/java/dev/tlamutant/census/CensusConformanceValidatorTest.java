package dev.tlamutant.census;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CensusConformanceValidatorTest {
    private static final String REPOSITORY = "https://github.com/tlaplus/Examples";
    private static final String COMMIT = "a".repeat(40);

    @Test
    void acceptsCompleteExactIdentityWithAClusterAtTwentyFivePercent() {
        CorpusManifest manifest = manifest();
        CorpusCensusReport report = report(manifest, 5);

        assertSame(report, CensusConformanceValidator.validate(manifest, report));
    }

    @Test
    void rejectsAnEnrichedEntryWithoutAConformingSite() {
        CorpusManifest manifest = manifest();
        CorpusCensusReport report = report(manifest, 5);
        List<CorpusCensusEntryResult> entries = new ArrayList<>(report.entries());
        CorpusCensusEntryResult enriched = entries.get(5);
        entries.set(5, completed(manifest.entries().get(5), List.of(site(90, false))));

        assertThrows(
                IllegalArgumentException.class,
                () -> CensusConformanceValidator.validate(manifest, withEntries(report, entries)));
    }

    @Test
    void rejectsEntryIdentityMismatchIncompleteEntryAndDuplicateSiteId() {
        CorpusManifest manifest = manifest();
        CorpusCensusReport valid = report(manifest, 5);

        List<CorpusCensusEntryResult> mismatched = new ArrayList<>(valid.entries());
        CorpusCensusEntryResult first = mismatched.get(0);
        mismatched.set(
                0,
                new CorpusCensusEntryResult(
                        "different-id",
                        first.domain(),
                        first.selection(),
                        first.status(),
                        first.diagnostic(),
                        first.report()));

        List<CorpusCensusEntryResult> incomplete = new ArrayList<>(valid.entries());
        CorpusManifest.Entry second = manifest.entries().get(1);
        incomplete.set(
                1,
                new CorpusCensusEntryResult(
                        second.id(), second.domain(), second.selection(), "TIMEOUT", "bounded timeout", null));

        List<CorpusCensusEntryResult> duplicate = new ArrayList<>(valid.entries());
        CorpusCensusEntryResult third = duplicate.get(2);
        GcdCensusSite duplicateSite = valid.entries().get(0).report().sites().get(0);
        duplicate.set(2, completed(manifest.entries().get(2), List.of(duplicateSite)));

        assertThrows(
                IllegalArgumentException.class,
                () -> CensusConformanceValidator.validate(manifest, withEntries(valid, mismatched)));
        assertThrows(
                IllegalArgumentException.class,
                () -> CensusConformanceValidator.validate(manifest, withEntries(valid, incomplete)));
        assertThrows(
                IllegalArgumentException.class,
                () -> CensusConformanceValidator.validate(manifest, withEntries(valid, duplicate)));
    }

    @Test
    void rejectsAClusterOwningMoreThanTwentyFivePercentOfAllSites() {
        CorpusManifest manifest = manifest();

        assertThrows(
                IllegalArgumentException.class,
                () -> CensusConformanceValidator.validate(manifest, report(manifest, 6)));
    }

    @Test
    void mutationGateRequiresSchemaThreeWithExactInputIdentity() {
        CorpusManifest manifest = manifest();
        CorpusCensusReport legacy = report(manifest, 5);
        CensusInputIdentity expected = inputs('a');
        CorpusCensusReport bound = new CorpusCensusReport(
                3, legacy.profile(), legacy.repository(), legacy.commit(), expected, legacy.entries());

        assertSame(bound, CensusConformanceValidator.validateBoundInputs(manifest, bound, expected));
        assertThrows(
                IllegalArgumentException.class,
                () -> CensusConformanceValidator.validateBoundInputs(manifest, legacy, expected));
        assertThrows(
                IllegalArgumentException.class,
                () -> CensusConformanceValidator.validateBoundInputs(manifest, bound, inputs('d')));
    }

    private static CensusInputIdentity inputs(char first) {
        return new CensusInputIdentity(
                new CensusInputIdentity.Artifact(String.valueOf(first).repeat(64), 10),
                new CensusInputIdentity.Artifact("b".repeat(64), 20),
                new CensusInputIdentity.Artifact("c".repeat(64), 30),
                new CensusInputIdentity.Artifact("e".repeat(64), 40));
    }

    private static CorpusCensusReport report(CorpusManifest manifest, int firstClusterSiteCount) {
        List<CorpusCensusEntryResult> entries = new ArrayList<>();
        int candidate = 0;
        for (int index = 0; index < manifest.entries().size(); index++) {
            int count = index == 0 ? firstClusterSiteCount : index == 1 ? 2 : 1;
            List<GcdCensusSite> sites = new ArrayList<>();
            for (int siteIndex = 0; siteIndex < count; siteIndex++) {
                sites.add(site(candidate++, index >= 5));
            }
            entries.add(completed(manifest.entries().get(index), sites));
        }
        return new CorpusCensusReport(1, manifest.profile(), REPOSITORY, COMMIT, entries);
    }

    private static CorpusCensusEntryResult completed(CorpusManifest.Entry entry, List<GcdCensusSite> sites) {
        return new CorpusCensusEntryResult(
                entry.id(),
                entry.domain(),
                entry.selection(),
                "COMPLETED",
                null,
                new GcdCensusReport(1, entry.rootModule(), entry.config(), sites));
    }

    private static GcdCensusSite site(int index, boolean conforms) {
        return new GcdCensusSite(
                "gcd-" + "%020x".formatted(index), "Spec", "Next", "x > 0", 0, 1, conforms ? 1 : 0, conforms);
    }

    private static CorpusCensusReport withEntries(CorpusCensusReport report, List<CorpusCensusEntryResult> entries) {
        return new CorpusCensusReport(
                report.schemaVersion(), report.profile(), report.repository(), report.commit(), entries);
    }

    private static CorpusManifest manifest() {
        List<CorpusManifest.Entry> entries = new ArrayList<>();
        List<String> strata = List.of(
                "guarded-admission-positive-control",
                "lifecycle-or-pc-ordering",
                "type-only-gap-prospect",
                "collision-or-capacity");
        for (int index = 0; index < 15; index++) {
            boolean control = index < 5;
            entries.add(entry(
                    (control ? "control-" : "enriched-") + index,
                    "domain-" + (index % 3),
                    new CorpusManifest.Selection(
                            control ? "control" : "gcd-enriched",
                            control ? "control-baseline" : strata.get(index % strata.size()),
                            "cluster-" + index,
                            "source-shape rationale")));
        }
        return new CorpusManifest(2, "gcd-stratified-v1", REPOSITORY, COMMIT, "MIT", entries);
    }

    private static CorpusManifest.Entry entry(String id, String domain, CorpusManifest.Selection selection) {
        ExecutionPolicy.Limits limits = new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90);
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
                limits,
                selection);
    }
}
