package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.mutation.MutationCandidate;
import dev.tlamutant.mutation.MutationFamily;
import dev.tlamutant.mutation.SourceDocument;
import dev.tlamutant.mutation.SourcePatcher;
import dev.tlamutant.mutation.SourceRange;
import dev.tlamutant.tlc.ExecutionStatus;
import dev.tlamutant.tlc.OracleOutcome;
import dev.tlamutant.tlc.TlcExecution;
import dev.tlamutant.tlc.TlcToolEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MutationEvidenceTest {
    @Test
    void reconstructsAndReversesTheExactMutation() {
        SourceDocument original =
                SourceDocument.fromString("---- MODULE Spec ----\nNext == /\\ x = 0 /\\ x' = 1\n====\n");
        int start = original.content().indexOf("x = 0");
        MutationCandidate candidate = MutationCandidate.create(
                MutationFamily.GCD, "Spec", "Next", new SourceRange(start, start + "x = 0".length()), "x = 0", "TRUE");
        SourceDocument patched = new SourcePatcher().apply(original, candidate);
        TlcExecution execution = new TlcExecution(
                ExecutionStatus.COMPLETED,
                OracleOutcome.KILLED_TEMPORAL_NAMED,
                13,
                List.of(
                        new TlcToolEvent(2116, 1, "Temporal property EventuallyDone was violated."),
                        new TlcToolEvent(2116, 1, "duplicate"),
                        new TlcToolEvent(2199, 0, "statistics")),
                List.of(),
                "bounded",
                "",
                false,
                "EventuallyDone");

        MutationEvidence evidence = MutationEvidence.create(candidate, original, patched, execution);

        assertEquals("Spec", evidence.module());
        assertEquals("Next", evidence.definition());
        assertEquals(start, evidence.range().startUtf16Offset());
        assertEquals(start + "x = 0".length(), evidence.range().endUtf16OffsetExclusive());
        assertEquals("EventuallyDone", evidence.violatedProperty());
        assertEquals(13, evidence.tlcExitCode());
        assertEquals(List.of(2116), evidence.toolEventCodes());
        assertEquals(patched.content(), evidence.patch().apply(original.content()));
        assertEquals(original.content(), evidence.patch().reverse(patched.content()));
    }

    @Test
    void refusesToApplyAPatchToChangedOrUnexpectedSource() {
        SourceDocument original = SourceDocument.fromString("0123456789");
        MutationCandidate candidate = MutationCandidate.create(
                MutationFamily.UOI, "Spec", "Value", new SourceRange(3, 6), "345", "(345) + 1");
        SourceDocument patched = new SourcePatcher().apply(original, candidate);
        MutationEvidence evidence = MutationEvidence.create(
                candidate,
                original,
                patched,
                new TlcExecution(
                        ExecutionStatus.COMPLETED, OracleOutcome.PASSED, 0, List.of(), List.of(), "", "", false));

        assertThrows(IllegalArgumentException.class, () -> evidence.patch().apply("012x456789"));
        assertThrows(IllegalArgumentException.class, () -> evidence.patch().reverse(patched.content() + "x"));
    }

    @Test
    void requiresAllFiveInputArtifacts() {
        ExperimentInputIdentity.Artifact artifact = new ExperimentInputIdentity.Artifact("a".repeat(64), 1);

        assertThrows(
                NullPointerException.class,
                () -> new ExperimentInputIdentity(null, artifact, artifact, artifact, artifact));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentInputIdentity.Artifact("A".repeat(64), 1));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentInputIdentity.Artifact("a".repeat(64), 0));
    }
}
