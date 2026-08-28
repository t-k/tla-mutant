package dev.tlamutant.mutation;

import dev.tlamutant.semantic.OperatorDefinition;
import dev.tlamutant.semantic.SemanticProject;
import dev.tlamutant.semantic.SemanticRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tla2sany.semantic.OpApplNode;
import tla2sany.semantic.OpDefNode;
import tla2sany.semantic.SemanticNode;

final class MutationSupport {
    private MutationSupport() {}

    static List<OperatorDefinition> nextModelDefinitions(SemanticProject project) {
        return project.eligibleModelDefinitions().stream()
                .filter(MutationSupport::isBelowConfiguredNext)
                .toList();
    }

    private static boolean isBelowConfiguredNext(OperatorDefinition definition) {
        String path = definition.rootPaths().getOrDefault(SemanticRole.MODEL, "");
        return path.startsWith("NEXT ") || path.endsWith(" -> Next") || path.contains(" -> Next -> ");
    }

    static boolean isEligibleReferencedDefinition(SemanticProject project, SemanticNode node) {
        if (!(node instanceof OpApplNode application) || !(application.getOperator() instanceof OpDefNode definition)) {
            return true;
        }
        OperatorDefinition referenced =
                project.definitions().get(definition.getName().toString());
        return referenced == null || referenced.eligibleForModelMutation();
    }

    static List<OpApplNode> applications(SemanticNode root) {
        List<OpApplNode> result = new ArrayList<>();
        collect(root, Set.of(), result);
        return List.copyOf(result);
    }

    private static void collect(SemanticNode node, Set<Integer> ancestors, List<OpApplNode> result) {
        if (node == null || ancestors.contains(node.getUid())) {
            return;
        }
        Set<Integer> nextAncestors = new java.util.HashSet<>(ancestors);
        nextAncestors.add(node.getUid());
        if (node instanceof OpApplNode application) {
            result.add(application);
        }
        SemanticNode[] children = node.getChildren();
        if (children != null) {
            for (SemanticNode child : children) {
                collect(child, nextAncestors, result);
            }
        }
    }

    static SourceRange operatorTokenRange(SourceDocument source, SemanticNode node, Pattern operatorPattern) {
        SourceRange expression = source.range(node.getLocation());
        String text = source.slice(expression);
        Matcher matcher = operatorPattern.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Expected one operator token in semantic range");
        }
        int start = matcher.start();
        int end = matcher.end();
        if (matcher.find()) {
            throw new IllegalArgumentException("Expected exactly one operator token in semantic range");
        }
        return new SourceRange(expression.start() + start, expression.start() + end);
    }
}
