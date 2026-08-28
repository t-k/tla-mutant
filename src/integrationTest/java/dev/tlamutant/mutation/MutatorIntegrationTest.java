package dev.tlamutant.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
