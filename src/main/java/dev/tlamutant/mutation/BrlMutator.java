package dev.tlamutant.mutation;

import dev.tlamutant.semantic.OperatorDefinition;
import dev.tlamutant.semantic.SemanticProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import tla2sany.semantic.OpApplNode;
import tlc2.tool.ToolGlobals;

public final class BrlMutator {
    private static final Pattern LESS = Pattern.compile("(?<![<=>])<(?![=>])");
    private static final Pattern GREATER = Pattern.compile("(?<![<=>])>(?![=>])");

    public List<MutationCandidate> generate(SemanticProject project, SourceDocument source) {
        List<MutationCandidate> candidates = new ArrayList<>();
        for (OperatorDefinition definition : MutationSupport.nextModelDefinitions(project)) {
            for (OpApplNode application :
                    MutationSupport.applications(definition.semanticNode().getBody())) {
                if (application.getLevel() > 1) {
                    continue;
                }
                String operator = application.getOperator().getName().toString();
                Pattern pattern;
                String replacement;
                if (operator.equals(ToolGlobals.OP_lt.toString())) {
                    pattern = LESS;
                    replacement = "<=";
                } else if (operator.equals(ToolGlobals.OP_gt.toString())) {
                    pattern = GREATER;
                    replacement = ">=";
                } else {
                    continue;
                }
                SourceRange range = MutationSupport.operatorTokenRange(source, application, pattern);
                candidates.add(MutationCandidate.create(
                        MutationFamily.BRL,
                        project.rootModule(),
                        definition.name(),
                        range,
                        source.slice(range),
                        replacement));
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt(candidate -> candidate.range().start()))
                .toList();
    }
}
