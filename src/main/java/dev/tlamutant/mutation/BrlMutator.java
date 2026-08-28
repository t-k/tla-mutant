package dev.tlamutant.mutation;

import dev.tlamutant.semantic.OperatorDefinition;
import dev.tlamutant.semantic.SemanticProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import tla2sany.semantic.ExprOrOpArgNode;
import tla2sany.semantic.OpApplNode;
import tla2sany.semantic.OpDefNode;
import tlc2.tool.ToolGlobals;

public final class BrlMutator {
    private static final Pattern LESS = Pattern.compile("(?<![<=>])<(?![=>])");
    private static final Pattern GREATER = Pattern.compile("(?<![<=>])>(?![=>])");

    public List<MutationCandidate> generate(SemanticProject project, SourceDocument source) {
        List<MutationCandidate> candidates = new ArrayList<>();
        List<OperatorDefinition> definitions = MutationSupport.nextModelDefinitions(project);
        Set<OpDefNode> referencedGuards = new HashSet<>();
        for (OperatorDefinition definition : definitions) {
            if (!(definition.semanticNode().getBody() instanceof OpApplNode conjunction)
                    || (!conjunction.hasOpcode(ToolGlobals.OPCODE_land)
                            && !conjunction.hasOpcode(ToolGlobals.OPCODE_cl))) {
                continue;
            }
            for (ExprOrOpArgNode argument : conjunction.getArgs()) {
                if (argument.getLevel() > 1) {
                    continue;
                }
                if (argument instanceof OpApplNode application
                        && application.getOperator() instanceof OpDefNode referenced) {
                    OperatorDefinition guard =
                            project.definitions().get(referenced.getName().toString());
                    if (guard != null && guard.eligibleForModelMutation()) {
                        referencedGuards.add(referenced);
                    }
                } else if (argument instanceof OpApplNode comparison) {
                    addComparison(candidates, project, definition, source, comparison);
                }
            }
        }
        for (OperatorDefinition definition : definitions) {
            if (referencedGuards.contains(definition.semanticNode())
                    && definition.semanticNode().getBody() instanceof OpApplNode comparison) {
                addComparison(candidates, project, definition, source, comparison);
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt(candidate -> candidate.range().start()))
                .toList();
    }

    private static void addComparison(
            List<MutationCandidate> candidates,
            SemanticProject project,
            OperatorDefinition definition,
            SourceDocument source,
            OpApplNode application) {
        if (application.getLevel() > 1) {
            return;
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
            return;
        }
        SourceRange range = MutationSupport.operatorTokenRange(source, application, pattern);
        candidates.add(MutationCandidate.create(
                MutationFamily.BRL, project.rootModule(), definition.name(), range, source.slice(range), replacement));
    }
}
