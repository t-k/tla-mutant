package dev.tlamutant.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class StateRelationAnalyzer {
    public record Analysis(
            StateRelation relation, Optional<CanonicalState> mutantOnlyWitness, Optional<String> reason) {
        public Analysis {
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(mutantOnlyWitness, "mutantOnlyWitness");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public Analysis analyze(List<CanonicalState> baseline, List<CanonicalState> mutant) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutant, "mutant");

        StateIndex baselineIndex = StateIndex.create(baseline);
        StateIndex mutantIndex = StateIndex.create(mutant);
        Set<String> baselineValues = baselineIndex.canonicalValues();
        Set<String> mutantValues = mutantIndex.canonicalValues();

        if (baselineValues.equals(mutantValues)) {
            return resolved(StateRelation.STATE_EQUAL, Optional.empty());
        }

        Optional<CanonicalState> witness = mutantIndex.firstStateAbsentFrom(baselineValues);
        if (mutantValues.containsAll(baselineValues)) {
            return resolved(StateRelation.STATE_EXPANDING, witness);
        }
        if (baselineValues.containsAll(mutantValues)) {
            return resolved(StateRelation.STATE_RESTRICTING, Optional.empty());
        }
        return resolved(StateRelation.STATE_INCOMPARABLE, witness);
    }

    public Analysis unresolved(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An unresolved relation requires a reason");
        }
        return new Analysis(StateRelation.STATE_UNRESOLVED, Optional.empty(), Optional.of(reason));
    }

    private static Analysis resolved(StateRelation relation, Optional<CanonicalState> witness) {
        return new Analysis(relation, witness, Optional.empty());
    }

    private record StateIndex(Map<String, List<CanonicalState>> byDigest) {
        private static StateIndex create(List<CanonicalState> states) {
            Map<String, List<CanonicalState>> index = new HashMap<>();
            for (CanonicalState state : states) {
                index.computeIfAbsent(state.sha256(), ignored -> new ArrayList<>())
                        .add(state);
            }
            return new StateIndex(index);
        }

        private Set<String> canonicalValues() {
            Set<String> values = new HashSet<>();
            byDigest.values().forEach(bucket -> bucket.forEach(state -> values.add(state.canonicalValue())));
            return Set.copyOf(values);
        }

        private Optional<CanonicalState> firstStateAbsentFrom(Set<String> otherValues) {
            return byDigest.values().stream()
                    .flatMap(List::stream)
                    .filter(state -> !otherValues.contains(state.canonicalValue()))
                    .sorted((left, right) -> left.canonicalValue().compareTo(right.canonicalValue()))
                    .findFirst();
        }
    }
}
