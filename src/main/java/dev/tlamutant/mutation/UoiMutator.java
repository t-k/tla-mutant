package dev.tlamutant.mutation;

import dev.tlamutant.semantic.OperatorDefinition;
import dev.tlamutant.semantic.SemanticProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tla2sany.semantic.ExprOrOpArgNode;
import tla2sany.semantic.NumeralNode;
import tla2sany.semantic.OpApplNode;
import tlc2.tool.ToolGlobals;

public final class UoiMutator {
    public List<MutationCandidate> generate(SemanticProject project, SourceDocument source) {
        return generate(project, source, Integer.MAX_VALUE);
    }

    public List<MutationCandidate> generate(SemanticProject project, SourceDocument source, int maximumCandidates) {
        return generate(
                project,
                source,
                maximumCandidates,
                MutationSupport.MAXIMUM_VISITED_SEMANTIC_NODES,
                MutationSupport.MAXIMUM_SEMANTIC_DEPTH);
    }

    List<MutationCandidate> generate(
            SemanticProject project,
            SourceDocument source,
            int maximumCandidates,
            int maximumVisitedNodes,
            int maximumDepth) {
        if (maximumCandidates < 0) {
            throw new IllegalArgumentException("UOI candidate bound cannot be negative");
        }
        List<MutationCandidate> candidates = new ArrayList<>();
        int remainingVisitedNodes = maximumVisitedNodes;
        for (OperatorDefinition definition : MutationSupport.nextModelDefinitions(project)) {
            int visitedNodes = MutationSupport.visitApplications(
                    definition.semanticNode().getBody(), remainingVisitedNodes, maximumDepth, equality -> {
                        if (!equality.hasOpcode(ToolGlobals.OPCODE_eq) || equality.getArgs().length != 2) {
                            return;
                        }
                        ExprOrOpArgNode left = equality.getArgs()[0];
                        ExprOrOpArgNode right = equality.getArgs()[1];
                        if (!(left instanceof OpApplNode prime) || !prime.hasOpcode(ToolGlobals.OPCODE_prime)) {
                            return;
                        }
                        if (right instanceof NumeralNode numeral) {
                            addLiteralCandidates(candidates, project, definition, source, numeral, maximumCandidates);
                        } else if (right instanceof OpApplNode arithmetic
                                && arithmetic.getOperator().getName().equals(ToolGlobals.OP_plus)
                                && arithmetic.getArgs().length == 2
                                && arithmetic.getArgs()[1] instanceof NumeralNode numeral
                                && isOne(numeral)) {
                            SourceRange range = source.range(numeral.getLocation());
                            requireCapacity(candidates, maximumCandidates);
                            candidates.add(MutationCandidate.create(
                                    MutationFamily.UOI,
                                    project.rootModule(),
                                    definition.name(),
                                    range,
                                    source.slice(range),
                                    "2"));
                        }
                    });
            remainingVisitedNodes -= visitedNodes;
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt((MutationCandidate candidate) ->
                                candidate.range().start())
                        .thenComparing(MutationCandidate::replacement))
                .toList();
    }

    private static boolean isOne(NumeralNode numeral) {
        return numeral.useVal() ? numeral.val() == 1 : java.math.BigInteger.ONE.equals(numeral.bigVal());
    }

    private static void addLiteralCandidates(
            List<MutationCandidate> candidates,
            SemanticProject project,
            OperatorDefinition definition,
            SourceDocument source,
            NumeralNode numeral,
            int maximumCandidates) {
        SourceRange range = source.range(numeral.getLocation());
        String original = source.slice(range);
        requireCapacity(candidates, maximumCandidates);
        candidates.add(MutationCandidate.create(
                MutationFamily.UOI, project.rootModule(), definition.name(), range, original, original + " + 1"));
        requireCapacity(candidates, maximumCandidates);
        candidates.add(MutationCandidate.create(
                MutationFamily.UOI, project.rootModule(), definition.name(), range, original, original + " - 1"));
    }

    private static void requireCapacity(List<MutationCandidate> candidates, int maximumCandidates) {
        if (candidates.size() >= maximumCandidates) {
            throw new IllegalArgumentException("UOI candidate count exceeds its bound");
        }
    }
}
