package dev.tlamutant.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tla2sany.modanalyzer.SpecObj;

public record SemanticProject(
        String rootModule,
        Map<String, OperatorDefinition> definitions,
        Set<String> transitionDefinitions,
        SpecObj specification) {
    public SemanticProject {
        Objects.requireNonNull(rootModule, "rootModule");
        definitions = Map.copyOf(definitions);
        transitionDefinitions = Set.copyOf(transitionDefinitions);
        Objects.requireNonNull(specification, "specification");
    }

    public OperatorDefinition definition(String name) {
        OperatorDefinition definition = definitions.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown operator definition: " + name);
        }
        return definition;
    }

    public List<OperatorDefinition> eligibleModelDefinitions() {
        return definitions.values().stream()
                .filter(OperatorDefinition::eligibleForModelMutation)
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .toList();
    }
}
