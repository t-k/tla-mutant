package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CorpusExperimentReportTest {
    @Test
    void fullPropertyCorpusReportBindsCommonFrozenInputs() {
        ExperimentInputIdentity.Artifact manifest = new ExperimentInputIdentity.Artifact("a".repeat(64), 10);
        ExperimentInputIdentity.Artifact tools = new ExperimentInputIdentity.Artifact("b".repeat(64), 20);
        ExperimentInputIdentity.Artifact application = new ExperimentInputIdentity.Artifact("c".repeat(64), 30);
        CorpusExperimentInputIdentity inputs = new CorpusExperimentInputIdentity(manifest, tools, application);

        CorpusExperimentReport report = CorpusExperimentReport.create(
                "https://github.com/example/project", "d".repeat(40), OracleMode.FULL_PROPERTY, inputs, List.of());

        assertEquals(2, report.schemaVersion());
        assertEquals("full-property", report.oracleMode());
        assertEquals(inputs, report.inputs());
    }

    @Test
    void fullPropertyCorpusRejectsLegacyOrMismatchedEntryEvidence() {
        ExperimentInputIdentity.Artifact manifest = new ExperimentInputIdentity.Artifact("a".repeat(64), 10);
        ExperimentInputIdentity.Artifact tools = new ExperimentInputIdentity.Artifact("b".repeat(64), 20);
        ExperimentInputIdentity.Artifact application = new ExperimentInputIdentity.Artifact("c".repeat(64), 30);
        CorpusExperimentInputIdentity inputs = new CorpusExperimentInputIdentity(manifest, tools, application);
        ExperimentReport legacy = new ExperimentReport(1, "A.tla", "A.cfg", "proxy-agent-review", 0, 0, 0, List.of());
        ExperimentInputIdentity mismatched = new ExperimentInputIdentity(
                new ExperimentInputIdentity.Artifact("d".repeat(64), 40),
                new ExperimentInputIdentity.Artifact("e".repeat(64), 50),
                new ExperimentInputIdentity.Artifact("f".repeat(64), 60),
                tools,
                application,
                List.of(
                        new ExperimentInputIdentity.ModelFile(
                                "A.cfg", new ExperimentInputIdentity.Artifact("e".repeat(64), 50)),
                        new ExperimentInputIdentity.ModelFile(
                                "A.tla", new ExperimentInputIdentity.Artifact("d".repeat(64), 40))));
        ExperimentReport wrongManifest = new ExperimentReport(
                3, "A.tla", "A.cfg", "proxy-agent-review", 0, 0, 0, List.of(), "full-property", mismatched);

        assertThrows(
                IllegalArgumentException.class,
                () -> CorpusExperimentReport.create(
                        "repository",
                        "1".repeat(40),
                        OracleMode.FULL_PROPERTY,
                        inputs,
                        List.of(new CorpusEntryResult("a", "domain", "COMPLETED", null, legacy))));
        assertThrows(
                IllegalArgumentException.class,
                () -> CorpusExperimentReport.create(
                        "repository",
                        "1".repeat(40),
                        OracleMode.FULL_PROPERTY,
                        inputs,
                        List.of(new CorpusEntryResult("a", "domain", "COMPLETED", null, wrongManifest))));
    }

    @Test
    void aggregatesOnlyCompletedEntryReports() {
        ExperimentReport completed =
                new ExperimentReport(1, "A.tla", "A.cfg", "proxy-agent-review", 3, 2, 1, List.of());

        CorpusExperimentReport report = CorpusExperimentReport.create(
                "https://github.com/tlaplus/Examples",
                "a".repeat(40),
                List.of(
                        new CorpusEntryResult("a", "puzzle", "COMPLETED", null, completed),
                        new CorpusEntryResult("b", "concurrency", "TIMEOUT", "bounded timeout", null)));

        assertEquals(2, report.entryCount());
        assertEquals(1, report.completedEntries());
        assertEquals(3, report.generatedMutants());
        assertEquals(2, report.evaluatedMutants());
        assertEquals(1, report.gapCandidates());
    }

    @Test
    void rejectsInconsistentAggregatesAndDuplicateEntryIds() {
        ExperimentReport completed =
                new ExperimentReport(1, "A.tla", "A.cfg", "proxy-agent-review", 3, 2, 1, List.of());
        CorpusEntryResult entry = new CorpusEntryResult("same", "domain", "COMPLETED", null, completed);

        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusExperimentReport(
                        1, "repository", "a".repeat(40), "review", 2, 1, 3, 2, 1, List.of(entry)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusExperimentReport(
                        1, "repository", "a".repeat(40), "review", 2, 2, 6, 4, 2, List.of(entry, entry)));
    }
}
