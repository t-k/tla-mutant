package dev.tlamutant.semantic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tla2sany.semantic.OpApplNode;
import tla2sany.semantic.OpDefNode;
import tla2sany.semantic.SemanticNode;

final class RoleGraph {
    record Root(SemanticRole role, String label, OpDefNode definition) {}

    private RoleGraph() {}

    static Map<String, OperatorDefinition> classify(String module, OpDefNode[] nodes, List<Root> roots) {
        Map<String, OpDefNode> byName = new LinkedHashMap<>();
        for (OpDefNode node : nodes) {
            byName.put(node.getName().toString(), node);
        }
        Map<OpDefNode, List<OpDefNode>> edges = new HashMap<>();
        for (OpDefNode node : nodes) {
            Set<OpDefNode> references = new HashSet<>();
            collectReferences(node.getBody(), new HashSet<>(), references);
            references.retainAll(byName.values());
            edges.put(
                    node,
                    references.stream()
                            .sorted(Comparator.comparing(
                                    reference -> reference.getName().toString()))
                            .toList());
        }

        Map<OpDefNode, EnumSet<SemanticRole>> roles = new HashMap<>();
        Map<OpDefNode, EnumMap<SemanticRole, String>> paths = new HashMap<>();
        roots.stream().sorted(Comparator.comparing(Root::label)).forEach(root -> traverse(root, edges, roles, paths));

        Map<String, OperatorDefinition> result = new LinkedHashMap<>();
        byName.forEach((name, node) -> {
            Set<SemanticRole> membership = roles.getOrDefault(node, EnumSet.noneOf(SemanticRole.class));
            Optional<String> reason = exclusionReason(node, membership);
            String source = node.getLocation().source();
            result.put(
                    name,
                    new OperatorDefinition(
                            name,
                            source,
                            node.getLevel(),
                            membership,
                            paths.getOrDefault(node, new EnumMap<>(SemanticRole.class)),
                            reason,
                            node));
        });
        return result;
    }

    private static Optional<String> exclusionReason(OpDefNode node, Set<SemanticRole> roles) {
        if (node.hasSource() && node.getSource() != node) {
            return Optional.of("ambiguous-instance-source");
        }
        if (roles.isEmpty()) {
            return Optional.of("unreachable");
        }
        if (roles.size() > 1) {
            return Optional.of("shared-coupled");
        }
        if (roles.equals(Set.of(SemanticRole.PROPERTY))) {
            return Optional.of("property-only");
        }
        if (roles.equals(Set.of(SemanticRole.SCOPE))) {
            return Optional.of("scope-only");
        }
        return Optional.empty();
    }

    private static void traverse(
            Root root,
            Map<OpDefNode, List<OpDefNode>> edges,
            Map<OpDefNode, EnumSet<SemanticRole>> roles,
            Map<OpDefNode, EnumMap<SemanticRole, String>> paths) {
        record Step(OpDefNode node, List<String> path) {}
        ArrayDeque<Step> queue = new ArrayDeque<>();
        queue.add(
                new Step(root.definition(), List.of(root.definition().getName().toString())));
        Set<OpDefNode> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Step step = queue.removeFirst();
            if (!visited.add(step.node())) {
                continue;
            }
            roles.computeIfAbsent(step.node(), ignored -> EnumSet.noneOf(SemanticRole.class))
                    .add(root.role());
            paths.computeIfAbsent(step.node(), ignored -> new EnumMap<>(SemanticRole.class))
                    .putIfAbsent(
                            root.role(),
                            root.label() + " -> "
                                    + String.join(
                                            " -> ",
                                            step.path().subList(1, step.path().size())));
            for (OpDefNode next : edges.getOrDefault(step.node(), List.of())) {
                List<String> nextPath = new ArrayList<>(step.path());
                nextPath.add(next.getName().toString());
                queue.addLast(new Step(next, List.copyOf(nextPath)));
            }
        }
    }

    private static void collectReferences(SemanticNode node, Set<Integer> visited, Set<OpDefNode> references) {
        if (node == null || !visited.add(node.getUid())) {
            return;
        }
        if (node instanceof OpApplNode application && application.getOperator() instanceof OpDefNode definition) {
            references.add(definition);
        }
        SemanticNode[] children = node.getChildren();
        if (children == null) {
            return;
        }
        for (SemanticNode child : children) {
            collectReferences(child, visited, references);
        }
    }
}
