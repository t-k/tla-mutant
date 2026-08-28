package dev.tlamutant.mutation;

import dev.tlamutant.semantic.OperatorDefinition;
import dev.tlamutant.semantic.SemanticProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tla2sany.semantic.ExprOrOpArgNode;
import tla2sany.semantic.OpApplNode;
import tlc2.tool.ToolGlobals;

public final class GcdMutator {
    public List<MutationCandidate> generate(SemanticProject project, SourceDocument source) {
        List<MutationCandidate> candidates = new ArrayList<>();
        for (OperatorDefinition definition : MutationSupport.nextModelDefinitions(project)) {
            if (!(definition.semanticNode().getBody() instanceof OpApplNode conjunction)
                    || (!conjunction.hasOpcode(ToolGlobals.OPCODE_land)
                            && !conjunction.hasOpcode(ToolGlobals.OPCODE_cl))) {
                continue;
            }
            for (ExprOrOpArgNode argument : conjunction.getArgs()) {
                if (argument.getLevel() > 1 || !MutationSupport.isEligibleReferencedDefinition(project, argument)) {
                    continue;
                }
                SourceRange range = source.range(argument.getLocation());
                String original = source.slice(range);
                candidates.add(MutationCandidate.create(
                        MutationFamily.GCD, project.rootModule(), definition.name(), range, original, "TRUE"));
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt(candidate -> candidate.range().start()))
                .toList();
    }
}
