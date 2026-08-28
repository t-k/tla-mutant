package dev.tlamutant.semantic;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tla2sany.semantic.OpDefNode;

public record OperatorDefinition(
        String name,
        String sourceModule,
        int level,
        Set<SemanticRole> roles,
        Map<SemanticRole, String> rootPaths,
        Optional<String> exclusionReason,
        OpDefNode semanticNode) {
    public OperatorDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceModule, "sourceModule");
        roles = Set.copyOf(roles);
        rootPaths = Map.copyOf(rootPaths);
        Objects.requireNonNull(exclusionReason, "exclusionReason");
        Objects.requireNonNull(semanticNode, "semanticNode");
    }

    public boolean eligibleForModelMutation() {
        return roles.equals(Set.of(SemanticRole.MODEL)) && exclusionReason.isEmpty();
    }
}
