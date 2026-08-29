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
                sites.add(new GcdSite(candidate, argument.getLevel(), remainingState, actionSuccessors));
            }
        }
        return sites.stream()
                .sorted(Comparator.comparingInt(site -> site.candidate().range().start()))
                .toList();
    }
}
