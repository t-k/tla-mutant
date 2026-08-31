package dev.tlamutant.mutation;

import dev.tlamutant.semantic.OperatorDefinition;
import dev.tlamutant.semantic.SemanticProject;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tla2sany.semantic.OpApplNode;
import tla2sany.semantic.OpDefNode;
import tla2sany.semantic.SemanticNode;

final class MutationSupport {
    static final int MAXIMUM_VISITED_SEMANTIC_NODES = 1_000_000;
    static final int MAXIMUM_SEMANTIC_DEPTH = 4_096;

    private MutationSupport() {}

    static List<OperatorDefinition> nextModelDefinitions(SemanticProject project) {
        return project.eligibleModelDefinitions().stream()
                .filter(definition -> definition.sourceModule().equals(project.rootModule()))
                .filter(definition -> project.transitionDefinitions().contains(definition.name()))
                .toList();
    }

    static boolean isEligibleReferencedDefinition(SemanticProject project, SemanticNode node) {
        if (!(node instanceof OpApplNode application) || !(application.getOperator() instanceof OpDefNode definition)) {
            return true;
        }
        OperatorDefinition referenced =
                project.definitions().get(definition.getName().toString());
        return referenced == null || referenced.eligibleForModelMutation();
    }

    static int visitApplications(SemanticNode root, Consumer<OpApplNode> visitor) {
        return visitApplications(root, MAXIMUM_VISITED_SEMANTIC_NODES, MAXIMUM_SEMANTIC_DEPTH, visitor);
    }

    static int visitApplications(
            SemanticNode root, int maximumVisitedNodes, int maximumDepth, Consumer<OpApplNode> visitor) {
        if (maximumVisitedNodes <= 0 || maximumDepth <= 0) {
            throw new IllegalArgumentException("Semantic traversal limits must be positive");
        }
        java.util.Objects.requireNonNull(visitor, "visitor");
        if (root == null) {
            return 0;
        }

        ArrayDeque<TraversalFrame> frames = new ArrayDeque<>();
        Set<Integer> activePath = new HashSet<>();
        int visitedNodes = 0;
        visitedNodes = push(root, maximumVisitedNodes, maximumDepth, visitedNodes, frames, activePath, visitor);
        while (!frames.isEmpty()) {
            TraversalFrame frame = frames.peek();
            if (frame.hasNextChild()) {
                SemanticNode child = frame.nextChild();
                if (child == null) {
                    continue;
                }
                if (!activePath.contains(child.getUid())) {
                    visitedNodes =
                            push(child, maximumVisitedNodes, maximumDepth, visitedNodes, frames, activePath, visitor);
                }
            } else {
                TraversalFrame completed = frames.pop();
                activePath.remove(completed.node().getUid());
            }
        }
        return visitedNodes;
    }

    private static int push(
            SemanticNode node,
            int maximumVisitedNodes,
            int maximumDepth,
            int visitedNodes,
            ArrayDeque<TraversalFrame> frames,
            Set<Integer> activePath,
            Consumer<OpApplNode> visitor) {
        if (visitedNodes >= maximumVisitedNodes) {
            throw new IllegalArgumentException("Semantic traversal node count exceeds its bound");
        }
        if (frames.size() >= maximumDepth) {
            throw new IllegalArgumentException("Semantic traversal depth exceeds its bound");
        }
        activePath.add(node.getUid());
        frames.push(new TraversalFrame(node));
        if (node instanceof OpApplNode application) {
            visitor.accept(application);
        }
        return visitedNodes + 1;
    }

    private static final class TraversalFrame {
        private static final SemanticNode[] NO_CHILDREN = new SemanticNode[0];
        private final SemanticNode node;
        private final SemanticNode[] children;
        private int nextChild;

        private TraversalFrame(SemanticNode node) {
            this.node = node;
            SemanticNode[] discovered = node.getChildren();
            this.children = discovered == null ? NO_CHILDREN : discovered;
        }

        private SemanticNode node() {
            return node;
        }

        private boolean hasNextChild() {
            return nextChild < children.length;
        }

        private SemanticNode nextChild() {
            return children[nextChild++];
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
