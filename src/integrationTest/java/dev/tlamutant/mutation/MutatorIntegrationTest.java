package dev.tlamutant.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.census.GcdCensusAnalyzer;
import dev.tlamutant.census.GcdCensusReport;
import dev.tlamutant.semantic.SanyAnalyzer;
import dev.tlamutant.semantic.SemanticProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MutatorIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesOnlyEligibleGcdBrlAndUoiCandidates() throws IOException {
        Path root = Path.of("src/testFixtures/resources/tla/mutations");
        SemanticProject project = new SanyAnalyzer().analyze(root, "MutationFixture.tla", "MutationFixture.cfg");
        SourceDocument source = SourceDocument.read(root.resolve("MutationFixture.tla"));

        List<MutationCandidate> gcd = new GcdMutator().generate(project, source);
        List<MutationCandidate> brl = new BrlMutator().generate(project, source);
        List<MutationCandidate> uoi = new UoiMutator().generate(project, source);

        assertEquals(
                List.of("GuardA", "GuardB"),
                gcd.stream().map(MutationCandidate::original).toList());
        assertEquals(
                List.of("<", ">"), brl.stream().map(MutationCandidate::original).toList());
        assertEquals(
                List.of("1", "0", "0"),
                uoi.stream().map(MutationCandidate::original).toList());
        assertEquals(
                List.of("2", "0 + 1", "0 - 1"),
                uoi.stream().map(MutationCandidate::replacement).toList());
        assertFalse(gcd.stream().anyMatch(candidate -> candidate.original().equals("Shared")));
        assertFalse(brl.stream().anyMatch(candidate -> candidate.definition().equals("Safety")));
        assertFalse(uoi.stream().anyMatch(candidate -> candidate.definition().equals("UnknownUpdate")));
    }

    @Test
    void everyCandidateAppliesOnePatchAndRemainsSanyValid() throws IOException {
        Path fixture = Path.of("src/testFixtures/resources/tla/mutations");
        SemanticProject project = new SanyAnalyzer().analyze(fixture, "MutationFixture.tla", "MutationFixture.cfg");
        SourceDocument source = SourceDocument.read(fixture.resolve("MutationFixture.tla"));
        List<MutationCandidate> candidates = new java.util.ArrayList<>();
        candidates.addAll(new GcdMutator().generate(project, source));
        candidates.addAll(new BrlMutator().generate(project, source));
        candidates.addAll(new UoiMutator().generate(project, source));

        for (MutationCandidate candidate : candidates) {
            Path workspace = Files.createDirectory(temporaryDirectory.resolve(candidate.id()));
            Files.copy(fixture.resolve("MutationFixture.cfg"), workspace.resolve("MutationFixture.cfg"));
            SourceDocument patched = new SourcePatcher().apply(source, candidate);
            Files.writeString(workspace.resolve("MutationFixture.tla"), patched.content());

            new SanyAnalyzer().analyze(workspace, "MutationFixture.tla", "MutationFixture.cfg");
        }
    }

    @Test
    void followsTheNextDefinitionBelowAConfiguredSpecification() throws IOException {
        Path root = Path.of("src/testFixtures/resources/tla/roles");
        SemanticProject project = new SanyAnalyzer().analyze(root, "RoleFixture.tla", "RoleFixture.cfg");
        SourceDocument source = SourceDocument.read(root.resolve("RoleFixture.tla"));

        assertEquals(
                List.of("Bound"),
                new GcdMutator()
                        .generate(project, source).stream()
                                .map(MutationCandidate::original)
                                .toList());
        assertEquals(
                List.of("<"),
                new BrlMutator()
                        .generate(project, source).stream()
                                .map(MutationCandidate::original)
                                .toList());
        assertEquals(
                List.of("1"),
                new UoiMutator()
                        .generate(project, source).stream()
                                .map(MutationCandidate::original)
                                .toList());
    }

    @Test
    void followsANonliteralActionBelowAConfiguredSpecification() throws IOException {
        Path root = Path.of("src/testFixtures/resources/tla/nonliteral-action");
        SemanticProject project = new SanyAnalyzer().analyze(root, "NamedAction.tla", "NamedAction.cfg");
        SourceDocument source = SourceDocument.read(root.resolve("NamedAction.tla"));

        List<MutationCandidate> candidates = new GcdMutator().generate(project, source);
        GcdCensusReport census = new GcdCensusAnalyzer().analyze(root, "NamedAction.tla", "NamedAction.cfg");

        assertEquals(
                List.of("Enabled"),
                candidates.stream().map(MutationCandidate::original).toList());
        assertEquals(
                candidates.stream().map(MutationCandidate::id).toList(),
                census.sites().stream().map(site -> site.candidateId()).toList());
        assertTrue(census.sites().getFirst().conforms());
    }

    @Test
    void excludesFairnessOnlyActionsFromConfiguredSpecification() throws IOException {
        Path root = Path.of("src/testFixtures/resources/tla/specification-fairness");
        SemanticProject project = new SanyAnalyzer().analyze(root, "FairSpec.tla", "FairSpec.cfg");
        SourceDocument source = SourceDocument.read(root.resolve("FairSpec.tla"));

        assertEquals(
                List.of("NextGuard"),
                new GcdMutator()
                        .generate(project, source).stream()
                                .map(MutationCandidate::original)
                                .toList());
        assertFalse(project.transitionDefinitions().contains("FairOnly"));
        assertTrue(project.transitionDefinitions().containsAll(List.of("Next", "NextGuard", "NextUpdate")));
    }

    @Test
    void classifiesOnlyPositiveCurrentStateTopLevelConjunctsAsConforming() throws IOException {
        Path root = Path.of("src/testFixtures/resources/tla/gcd-shapes");

        GcdCensusReport report = new GcdCensusAnalyzer().analyze(root, "GcdShapes.tla", "GcdShapes.cfg");

        assertEquals(
                List.of("Positive", "x < 10"),
                report.sites().stream()
                        .filter(site -> site.definition().equals("Next"))
                        .filter(site -> site.conforms())
                        .map(site -> site.guard())
                        .toList());
        assertEquals(
                List.of("~Positive", "Conditional", "Nested", "ENABLED Primed"),
                report.sites().stream()
                        .filter(site -> site.definition().equals("Next"))
                        .filter(site -> !site.conforms())
                        .map(site -> site.guard())
                        .toList());
    }

    @Test
    void sharesExactGcdEligibilityWhileSeparatingSuccessorCapableSites() throws IOException {
        Path root = Path.of("src/testFixtures/resources/tla/census");
        SemanticProject project = new SanyAnalyzer().analyze(root, "CensusFixture.tla", "CensusFixture.cfg");
        SourceDocument source = SourceDocument.read(root.resolve("CensusFixture.tla"));

        List<GcdSite> sites = new GcdSiteAnalyzer().analyze(project, source);
        List<MutationCandidate> candidates = new GcdMutator().generate(project, source);

        assertEquals(
                candidates.stream().map(MutationCandidate::id).toList(),
                sites.stream().map(site -> site.candidate().id()).toList());
        assertTrue(sites.stream().allMatch(site -> site.guardLevel() <= 1));
        assertTrue(sites.stream()
                .anyMatch(site -> site.candidate().definition().equals("Action") && site.hasActionSuccessor()));
        assertTrue(sites.stream()
                .anyMatch(site -> site.candidate().definition().equals("StateOnly") && !site.hasActionSuccessor()));
    }

    @Test
    void serializesOnlyBoundedOutcomeFreeGcdStructure() throws IOException {
        Path root = Path.of("src/testFixtures/resources/tla/census");

        GcdCensusReport report = new GcdCensusAnalyzer().analyze(root, "CensusFixture.tla", "CensusFixture.cfg");
        String json = new ObjectMapper().writeValueAsString(report);

        assertEquals(1, report.schemaVersion());
        assertEquals("CensusFixture.tla", report.module());
        assertTrue(report.sites().stream().anyMatch(site -> site.conforms() && site.actionSuccessorConjuncts() > 0));
        assertTrue(report.sites().stream().allMatch(site -> site.guard().length() <= 500));
        assertFalse(json.contains("verdict"));
        assertFalse(json.contains("stateRelation"));
        assertFalse(json.contains("mutantOnlyWitness"));
    }
}
