package dev.tlamutant.mutation;

import dev.tlamutant.semantic.OperatorDefinition;
import dev.tlamutant.semantic.SemanticProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tla2sany.semantic.ExprOrOpArgNode;
import tla2sany.semantic.OpApplNode;
import tlc2.tool.ToolGlobals;

public final class GcdSiteAnalyzer {
    public List<GcdSite> analyze(SemanticProject project, SourceDocument source) {
        List<GcdSite> sites = new ArrayList<>();
        for (OperatorDefinition definition : MutationSupport.nextModelDefinitions(project)) {
            if (!(definition.semanticNode().getBody() instanceof OpApplNode conjunction)
                    || (!conjunction.hasOpcode(ToolGlobals.OPCODE_land)
                            && !conjunction.hasOpcode(ToolGlobals.OPCODE_cl))) {
                continue;
            }
            ExprOrOpArgNode[] arguments = conjunction.getArgs();
            for (ExprOrOpArgNode argument : arguments) {
                if (argument.getLevel() > 1 || !MutationSupport.isEligibleReferencedDefinition(project, argument)) {
                    continue;
                }
                SourceRange range = source.range(argument.getLocation());
                MutationCandidate candidate = MutationCandidate.create(
                        MutationFamily.GCD,
                        project.rootModule(),
                        definition.name(),
                        range,
                        source.slice(range),
                        "TRUE");
                int remainingState = 0;
                int actionSuccessors = 0;
                for (ExprOrOpArgNode sibling : arguments) {
                    if (sibling == argument) {
                        continue;
                    }
                    if (sibling.getLevel() <= 1) {
                        remainingState++;
                    } else {
                        actionSuccessors++;
                    }
                }
                boolean conforms = actionSuccessors > 0
                        && isPositiveCurrentStateGuard(project, argument, new java.util.HashSet<>());
                sites.add(new GcdSite(candidate, argument.getLevel(), remainingState, actionSuccessors, conforms));
            }
        }
        return sites.stream()
                .sorted(Comparator.comparingInt(site -> site.candidate().range().start()))
                .toList();
    }

    private static boolean isPositiveCurrentStateGuard(
            SemanticProject project, tla2sany.semantic.SemanticNode node, java.util.Set<Integer> visited) {
        if (node == null
                || (node instanceof tla2sany.semantic.LevelNode levelNode && levelNode.getLevel() > 1)
                || visited.contains(node.getUid())) {
            return false;
        }
        java.util.Set<Integer> ancestors = new java.util.HashSet<>(visited);
        ancestors.add(node.getUid());
        if (node instanceof tla2sany.semantic.LetInNode letIn) {
            return isPositiveCurrentStateGuard(project, letIn.getBody(), ancestors);
        }
        if (node instanceof tla2sany.semantic.SubstInNode substitution) {
            return isPositiveCurrentStateGuard(project, substitution.getBody(), ancestors);
        }
        if (node instanceof OpApplNode application) {
            int opcode =
                    tlc2.tool.BuiltInOPs.getOpCode(application.getOperator().getName());
            if (opcode == ToolGlobals.OPCODE_lnot
                    || opcode == ToolGlobals.OPCODE_ite
                    || opcode == ToolGlobals.OPCODE_case
                    || opcode == ToolGlobals.OPCODE_implies
                    || opcode == ToolGlobals.OPCODE_equiv
                    || opcode == ToolGlobals.OPCODE_enabled
                    || tlc2.tool.BuiltInOPs.isAction(opcode)
                    || tlc2.tool.BuiltInOPs.isTemporal(opcode)) {
                return false;
            }
            if (opcode == 0 && application.getOperator() instanceof tla2sany.semantic.OpDefNode definition) {
                OperatorDefinition referenced =
                        project.definitions().get(definition.getName().toString());
                if (referenced != null) {
                    return isPositiveCurrentStateGuard(project, definition.getBody(), ancestors);
                }
            }
            for (tla2sany.semantic.ExprOrOpArgNode argument : application.getArgs()) {
                if (!isPositiveCurrentStateGuard(project, argument, ancestors)) {
                    return false;
                }
            }
            for (tla2sany.semantic.ExprNode bound : application.getBdedQuantBounds()) {
                if (!isPositiveCurrentStateGuard(project, bound, ancestors)) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }
}
