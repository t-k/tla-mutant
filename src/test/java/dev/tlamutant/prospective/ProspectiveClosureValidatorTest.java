package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.security.BoundedFileSnapshot;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProspectiveClosureValidatorTest {
    private static final String ENTRY = "nbacg-guer01";
    private static final String CANDIDATE = "gcd-3c1ee8b4429ee7af9c2f";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsAdoptedKillWithoutTypeOnlyRelationAndWitness() throws Exception {
        Fixture fixture = fixture(false);

        ProspectiveClosureAssessment assessment = ProspectiveClosureValidator.evaluate(
                fixture.dossier(), fixture.ledger(), fixture.review(), fixture.source(), fixture.intervention());

        assertEquals("UNRESOLVED", assessment.closureStatus());
        assertEquals(0, assessment.closed());
        assertEquals(1, assessment.unresolved());
        assertEquals("UNRESOLVED", assessment.outcomes().getFirst().status());
    }

    @Test
    void verifiesOnlyAnExactTypeOnlyExpansionWitnessAndAdoptedKill() throws Exception {
        Fixture fixture = fixture(true);

        ProspectiveClosureAssessment assessment = ProspectiveClosureValidator.evaluate(
                fixture.dossier(), fixture.ledger(), fixture.review(), fixture.source(), fixture.intervention());

        assertEquals("VERIFIED", assessment.closureStatus());
        assertEquals(1, assessment.selected());
        assertEquals(1, assessment.closed());
        assertEquals(0, assessment.unresolved());
        assertEquals("CLOSED", assessment.outcomes().getFirst().status());
    }

    @Test
    void rejectsASelectedIdWhoseMutationDefinitionWasSubstituted() throws Exception {
        Fixture fixture = fixture(true);
        ObjectNode interventionJson = (ObjectNode) mapper.valueToTree(fixture.intervention());
        ObjectNode target = (ObjectNode) findResult(interventionJson, ENTRY, CANDIDATE);
        target.put("definition", "SubstitutedDefinition");
        ProspectiveDiscoveryReport substituted = mapper.treeToValue(interventionJson, ProspectiveDiscoveryReport.class);

        ProspectiveClosureAssessment assessment = ProspectiveClosureValidator.evaluate(
                fixture.dossier(), fixture.ledger(), fixture.review(), fixture.source(), substituted);

        assertEquals("UNRESOLVED", assessment.closureStatus());
        assertEquals("UNRESOLVED", assessment.outcomes().getFirst().status());
    }

    private Fixture fixture(boolean corrected) throws Exception {
        ProspectiveReviewDossier dossier = read(
                Path.of("docs/experiments/gcd-prospective-review-dossier-v10.json"), ProspectiveReviewDossier.class);
        ProspectiveReviewLedger ledger =
                read(Path.of("docs/experiments/gcd-prospective-review-v10.json"), ProspectiveReviewLedger.class);
        ProspectiveProtocol protocol = read(Path.of("config/prospective-protocol-v10.json"), ProspectiveProtocol.class);
        Path dossierPath = Path.of("docs/experiments/gcd-prospective-review-dossier-v10.json");
        Path protocolPath = Path.of("config/prospective-protocol-v10.json");
        ProspectiveReviewValidator.ValidatedReview review = ProspectiveReviewValidator.validate(
                dossier, artifact(dossierPath), ledger, protocol, artifact(protocolPath));
        ProspectiveDiscoveryReport source =
                read(Path.of("docs/experiments/gcd-prospective-v10.json"), ProspectiveDiscoveryReport.class);
        JsonNode interventionJson = mapper.readTree(
                Path.of("docs/experiments/gcd-prospective-v11-incomplete.json").toFile());
        if (corrected) {
            JsonNode sourceResult = findResult(sourceNode(source), ENTRY, CANDIDATE);
            ObjectNode target = (ObjectNode) findResult(interventionJson, ENTRY, CANDIDATE);
            ObjectNode attribution = (ObjectNode) target.required("attribution");
            attribution.put(
                    "typeOnlyStateRelation",
                    sourceResult.required("stateRelation").textValue());
            attribution.put(
                    "typeOnlyMutantOnlyWitness",
                    sourceResult.required("mutantOnlyWitness").textValue());
        }
        ProspectiveDiscoveryReport intervention =
                mapper.treeToValue(interventionJson, ProspectiveDiscoveryReport.class);
        return new Fixture(dossier, ledger, review, source, intervention);
    }

    private JsonNode sourceNode(ProspectiveDiscoveryReport report) {
        return mapper.valueToTree(report);
    }

    private static JsonNode findResult(JsonNode report, String entry, String candidate) {
        for (JsonNode outcome : report.required("gcdOutcomes")) {
            if (outcome.required("entryId").textValue().equals(entry)
                    && outcome.required("result").required("id").textValue().equals(candidate)) {
                return outcome.required("result");
            }
        }
        throw new IllegalArgumentException("Test fixture candidate is missing");
    }

    private <T> T read(Path path, Class<T> type) throws Exception {
        return mapper.readValue(path.toFile(), type);
    }

    private static CensusInputIdentity.Artifact artifact(Path path) throws Exception {
        BoundedFileSnapshot snapshot = BoundedFileSnapshot.read(path, 256L * 1024 * 1024);
        return new CensusInputIdentity.Artifact(snapshot.sha256(), snapshot.sizeBytes());
    }

    private record Fixture(
            ProspectiveReviewDossier dossier,
            ProspectiveReviewLedger ledger,
            ProspectiveReviewValidator.ValidatedReview review,
            ProspectiveDiscoveryReport source,
            ProspectiveDiscoveryReport intervention) {}
}
