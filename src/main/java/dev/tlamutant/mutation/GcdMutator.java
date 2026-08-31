package dev.tlamutant.mutation;

import dev.tlamutant.semantic.SemanticProject;
import java.util.List;

public final class GcdMutator {
    public List<MutationCandidate> generate(SemanticProject project, SourceDocument source) {
        return generate(project, source, Integer.MAX_VALUE);
    }

    public List<MutationCandidate> generate(SemanticProject project, SourceDocument source, int maximumCandidates) {
        return new GcdSiteAnalyzer()
                .analyze(project, source, maximumCandidates).stream()
                        .map(GcdSite::candidate)
                        .toList();
    }
}
