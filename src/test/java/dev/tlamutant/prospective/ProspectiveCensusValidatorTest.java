package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.census.CorpusCensusEntryResult;
import dev.tlamutant.census.CorpusCensusReport;
import dev.tlamutant.census.GcdCensusReport;
import dev.tlamutant.census.GcdCensusSite;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveCensusValidatorTest {
    private static final CensusInputIdentity INPUTS = new CensusInputIdentity(
            new CensusInputIdentity.Artifact("a".repeat(64), 10),
            new CensusInputIdentity.Artifact("b".repeat(64), 20),
            new CensusInputIdentity.Artifact("c".repeat(64), 30),
            new CensusInputIdentity.Artifact("d".repeat(64), 40));
    private static final CensusInputIdentity.Artifact RECEIPT = new CensusInputIdentity.Artifact("e".repeat(64), 50);

    @Test
    void retainsCompletedZeroSiteAndFailedEntriesInExactOrder() {
        CorpusManifest manifest = manifest();
        List<CorpusCensusEntryResult> entries = terminalEntries(manifest);
        ProspectiveCensusReport report = report(manifest, entries);

        assertSame(report, ProspectiveCensusValidator.validate(manifest, report, RECEIPT, INPUTS));
        assertEquals(8, report.census().entries().size());
        assertEquals(0, report.census().entries().get(1).report().sites().size());
        assertEquals("TIMEOUT", report.census().entries().get(2).status());
    }

    @Test
    void rejectsOmissionReplacementAndReordering() {
        CorpusManifest manifest = manifest();
        List<CorpusCensusEntryResult> original = terminalEntries(manifest);
        List<CorpusCensusEntryResult> omitted = new ArrayList<>(original.subList(0, 7));
        List<CorpusCensusEntryResult> replacement = new ArrayList<>(original);
        CorpusCensusEntryResult first = replacement.getFirst();
        replacement.set(
                0,
                new CorpusCensusEntryResult(
                        "replacement",
                        first.domain(),
                        first.selection(),
                        first.status(),
                        first.diagnostic(),
                        first.report()));
        List<CorpusCensusEntryResult> reordered = new ArrayList<>(original);
        Collections.swap(reordered, 0, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, report(manifest, omitted), RECEIPT, INPUTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, report(manifest, replacement), RECEIPT, INPUTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, report(manifest, reordered), RECEIPT, INPUTS));
    }

    @Test
    void rejectsReceiptAndRunnerInputIdentityMismatch() {
        CorpusManifest manifest = manifest();
        ProspectiveCensusReport valid = report(manifest, terminalEntries(manifest));
        CensusInputIdentity.Artifact differentReceipt = new CensusInputIdentity.Artifact("f".repeat(64), 50);
        CensusInputIdentity differentInputs = new CensusInputIdentity(
                new CensusInputIdentity.Artifact("9".repeat(64), 10),
                INPUTS.policy(),
                INPUTS.tools(),
                INPUTS.application());

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, valid, differentReceipt, INPUTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, valid, RECEIPT, differentInputs));
    }

    @Test
    void rejectsDuplicateCandidateIdsAcrossEntriesAndReportIdentityMismatch() {
        CorpusManifest manifest = manifest();
        List<CorpusCensusEntryResult> entries = terminalEntries(manifest);
        GcdCensusSite duplicate = entries.getFirst().report().sites().getFirst();
        CorpusManifest.Entry fourth = manifest.entries().get(3);
        entries.set(3, completed(fourth, List.of(duplicate)));
        ProspectiveCensusReport wrongModule = report(
                manifest,
                replace(
                        terminalEntries(manifest),
                        0,
                        new CorpusCensusEntryResult(
                                "entry-0",
                                "domain-0",
                                manifest.entries().getFirst().selection(),
                                "COMPLETED",
                                null,
                                new GcdCensusReport(1, "Wrong.tla", "Spec0.cfg", List.of(site(40))))));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, report(manifest, entries), RECEIPT, INPUTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, wrongModule, RECEIPT, INPUTS));
    }

    @Test
    void rejectsUnknownFailureStatusOrUnsafeDiagnostic() {
        CorpusManifest manifest = manifest();
        CorpusManifest.Entry third = manifest.entries().get(2);
        List<CorpusCensusEntryResult> unknown = replace(
                terminalEntries(manifest),
                2,
                new CorpusCensusEntryResult(third.id(), third.domain(), third.selection(), "UNKNOWN", "failed", null));
        List<CorpusCensusEntryResult> unsafe = replace(
                terminalEntries(manifest),
                2,
                new CorpusCensusEntryResult(
                        third.id(), third.domain(), third.selection(), "TIMEOUT", "hidden\u202Etext", null));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, report(manifest, unknown), RECEIPT, INPUTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveCensusValidator.validate(manifest, report(manifest, unsafe), RECEIPT, INPUTS));
    }

    private static ProspectiveCensusReport report(CorpusManifest manifest, List<CorpusCensusEntryResult> entries) {
        return new ProspectiveCensusReport(
                1,
                RECEIPT,
                new CorpusCensusReport(
                        3, manifest.profile(), manifest.repository(), manifest.commit(), INPUTS, entries));
    }

    private static List<CorpusCensusEntryResult> terminalEntries(CorpusManifest manifest) {
        List<CorpusCensusEntryResult> entries = new ArrayList<>();
        entries.add(completed(manifest.entries().get(0), List.of(site(0))));
        entries.add(completed(manifest.entries().get(1), List.of()));
        CorpusManifest.Entry failed = manifest.entries().get(2);
        entries.add(new CorpusCensusEntryResult(
                failed.id(), failed.domain(), failed.selection(), "TIMEOUT", "bounded timeout", null));
        for (int index = 3; index < 8; index++) {
            entries.add(completed(manifest.entries().get(index), List.of(site(index))));
        }
        return entries;
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

    private static GcdCensusSite site(int index) {
        return new GcdCensusSite("gcd-" + "%020x".formatted(index), "Spec", "Next", "x > 0", 0, 1, 1, true);
    }

    private static List<CorpusCensusEntryResult> replace(
            List<CorpusCensusEntryResult> values, int index, CorpusCensusEntryResult replacement) {
        List<CorpusCensusEntryResult> changed = new ArrayList<>(values);
        changed.set(index, replacement);
        return changed;
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
}
