package dev.tlamutant.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SanyAnalyzerIntegrationTest {
    @Test
    void computesConfiguredSemanticRolesAndExcludesCoupledDefinitions() {
        Path root = Path.of("src/testFixtures/resources/tla/roles");

        SemanticProject project = new SanyAnalyzer().analyze(root, "RoleFixture.tla", "RoleFixture.cfg");

        assertEquals(Set.of(SemanticRole.MODEL), project.definition("Bound").roles());
        assertTrue(project.definition("Bound").eligibleForModelMutation());
        assertEquals(
                Set.of(SemanticRole.MODEL, SemanticRole.PROPERTY),
                project.definition("Shared").roles());
        assertFalse(project.definition("Shared").eligibleForModelMutation());
        assertEquals(
                "shared-coupled", project.definition("Shared").exclusionReason().orElseThrow());
        assertEquals(
                Set.of(SemanticRole.PROPERTY),
                project.definition("PropertyHelper").roles());
        assertEquals(
                Set.of(SemanticRole.SCOPE), project.definition("ScopeHelper").roles());
        assertTrue(project.definition("Unreachable").roles().isEmpty());
        assertEquals(
                "unreachable",
                project.definition("Unreachable").exclusionReason().orElseThrow());
    }

    @Test
    void recordsRootToDefinitionReferencePaths() {
        Path root = Path.of("src/testFixtures/resources/tla/roles");

        SemanticProject project = new SanyAnalyzer().analyze(root, "RoleFixture.tla", "RoleFixture.cfg");

        assertEquals(
                "SPECIFICATION Spec -> Next -> Shared",
                project.definition("Shared").rootPaths().get(SemanticRole.MODEL));
        assertEquals(
                "INVARIANT Safety -> Shared",
                project.definition("Shared").rootPaths().get(SemanticRole.PROPERTY));
    }
}
