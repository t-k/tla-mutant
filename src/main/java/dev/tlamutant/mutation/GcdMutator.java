package dev.tlamutant.mutation;

import dev.tlamutant.semantic.SemanticProject;
import java.util.List;

public final class GcdMutator {
    public List<MutationCandidate> generate(SemanticProject project, SourceDocument source) {
        return new GcdSiteAnalyzer()
                .analyze(project, source).stream().map(GcdSite::candidate).toList();
    }
}
