package dev.tlamutant.experiment;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ExperimentReportTest {
    @Test
    void acceptsAConsistentFullPropertyReport() throws Exception {
        Fixture fixture = fixture();

        ExperimentReport report = report(fixture.result(), 1, 1, 0, fixture.inputs());

        assertEquals(1, report.generatedMutants());
    }

    @Test
    void recomputesFullPropertyTotalsAndRejectsContradictoryVerdicts() throws Exception {
        Fixture fixture = fixture();
        MutationResult contradictory = copy(
                fixture.result(),
                fixture.result().id(),
                "TOOL_ERROR",
                "KILLED_TEMPORAL_NAMED",
                "full-property-temporal-kill");

        assertThrows(IllegalArgumentException.class, () -> report(fixture.result(), 1, 0, 0, fixture.inputs()));
        assertThrows(IllegalArgumentException.class, () -> report(fixture.result(), 1, 1, 1, fixture.inputs()));
        assertThrows(IllegalArgumentException.class, () -> report(contradictory, 1, 0, 0, fixture.inputs()));
    }

    @Test
    void bindsCandidateIdentityAndEveryEvidenceField() throws Exception {
        Fixture fixture = fixture();
        MutationResult wrongId = copy(
                fixture.result(),
                "gcd-00000000000000000000",
                "COMPLETED",
                "KILLED_TEMPORAL_NAMED",
                "full-property-temporal-kill");
        MutationEvidence evidence = fixture.result().evidence();
        MutationEvidence wrongModule = new MutationEvidence(
                "Other",
                evidence.definition(),
                evidence.range(),
                evidence.sourceSha256(),
                evidence.mutatedSourceSha256(),
                evidence.patch(),
                evidence.violatedProperty(),
                evidence.tlcExitCode(),
                evidence.toolEventCodes());
        MutationResult wrongEvidence = new MutationResult(
                fixture.result().id(),
                fixture.result().family(),
                fixture.result().definition(),
                fixture.result().original(),
                fixture.result().replacement(),
                fixture.result().executionStatus(),
                fixture.result().oracleOutcome(),
                fixture.result().stateRelation(),
                fixture.result().verdict(),
                fixture.result().diagnosticReason(),
                fixture.result().baselineStateCount(),
                fixture.result().mutantStateCount(),
                fixture.result().mutantOnlyWitness(),
                null,
                wrongModule);

        assertThrows(IllegalArgumentException.class, () -> report(wrongId, 1, 1, 0, fixture.inputs()));
        assertThrows(IllegalArgumentException.class, () -> report(wrongEvidence, 1, 1, 0, fixture.inputs()));
    }

    private static ExperimentReport report(
            MutationResult result, int generated, int evaluated, int survivors, ExperimentInputIdentity inputs) {
        return new ExperimentReport(
                3,
                "Spec.tla",
                "Spec.cfg",
                "proxy-agent-review",
                generated,
                evaluated,
                survivors,
                List.of(result),
                "full-property",
                inputs);
    }

    private static MutationResult copy(MutationResult source, String id, String status, String oracle, String verdict) {
        return new MutationResult(
                id,
                source.family(),
                source.definition(),
                source.original(),
                source.replacement(),
                status,
                oracle,
                source.stateRelation(),
                verdict,
                source.diagnosticReason(),
                source.baselineStateCount(),
                source.mutantStateCount(),
                source.mutantOnlyWitness(),
                null,
                source.evidence());
    }

    private static Fixture fixture() throws Exception {
        SourceDocument original =
                SourceDocument.fromString("---- MODULE Spec ----\nNext == /\\ x = 0 /\\ x' = 1\n====\n");
        int start = original.content().indexOf("x = 0");
        MutationCandidate candidate = MutationCandidate.create(
                MutationFamily.GCD, "Spec", "Next", new SourceRange(start, start + 5), "x = 0", "TRUE");
        SourceDocument patched = new SourcePatcher().apply(original, candidate);
        TlcExecution execution = new TlcExecution(
                ExecutionStatus.COMPLETED,
                OracleOutcome.KILLED_TEMPORAL_NAMED,
                13,
                List.of(
                        new TlcToolEvent(2116, 1, "Temporal property Live was violated."),
                        new TlcToolEvent(2217, 1, "trace")),
                List.of(),
                "",
                "",
                false,
                "Live");
        MutationEvidence evidence = MutationEvidence.create(candidate, original, patched, execution);
        MutationResult result = new MutationResult(
                candidate.id(),
                candidate.family().name(),
                candidate.definition(),
                candidate.original(),
                candidate.replacement(),
                execution.status().name(),
                execution.oracle().name(),
                "STATE_UNRESOLVED",
                "full-property-temporal-kill",
                null,
                2,
                3,
                null,
                null,
                evidence);
        ExperimentInputIdentity.Artifact module = artifact(original.content());
        ExperimentInputIdentity.Artifact config = artifact("SPECIFICATION Spec\n");
        ExperimentInputIdentity.Artifact other = artifact("other\n");
        ExperimentInputIdentity inputs = new ExperimentInputIdentity(
                module,
                config,
                other,
                other,
                other,
                List.of(
                        new ExperimentInputIdentity.ModelFile("Spec.cfg", config),
                        new ExperimentInputIdentity.ModelFile("Spec.tla", module)));
        return new Fixture(result, inputs);
    }

    private static ExperimentInputIdentity.Artifact artifact(String content) throws Exception {
        byte[] bytes = content.getBytes(UTF_8);
        return new ExperimentInputIdentity.Artifact(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), bytes.length);
    }

    private record Fixture(MutationResult result, ExperimentInputIdentity inputs) {}
}
