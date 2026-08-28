package dev.tlamutant.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class StateRelationAnalyzerTest {
    private final StateRelationAnalyzer analyzer = new StateRelationAnalyzer();

    @ParameterizedTest
    @MethodSource("relations")
    void classifiesCompleteStateSets(
            List<CanonicalState> baseline, List<CanonicalState> mutant, StateRelation expected) {
        assertEquals(expected, analyzer.analyze(baseline, mutant).relation());
    }

    static Stream<Arguments> relations() {
        CanonicalState a = CanonicalState.of("{\"x\":1}");
        CanonicalState b = CanonicalState.of("{\"x\":2}");
        CanonicalState c = CanonicalState.of("{\"x\":3}");
        return Stream.of(
                Arguments.of(List.of(a, b), List.of(b, a), StateRelation.STATE_EQUAL),
                Arguments.of(List.of(a), List.of(a, b), StateRelation.STATE_EXPANDING),
                Arguments.of(List.of(a, b), List.of(a), StateRelation.STATE_RESTRICTING),
                Arguments.of(List.of(a, b), List.of(b, c), StateRelation.STATE_INCOMPARABLE));
    }

    @Test
    void expandingAndIncomparableRelationsCarryAMutantOnlyWitness() {
        CanonicalState baseline = CanonicalState.of("{\"x\":1}");
        CanonicalState witness = CanonicalState.of("{\"x\":2}");

        StateRelationAnalyzer.Analysis analysis = analyzer.analyze(List.of(baseline), List.of(witness));

        assertEquals(StateRelation.STATE_INCOMPARABLE, analysis.relation());
        assertEquals(
                witness.canonicalValue(),
                analysis.mutantOnlyWitness().orElseThrow().canonicalValue());
    }

    @Test
    void aDigestCollisionCannotEstablishStateEquality() {
        String collision = "00".repeat(32);
        CanonicalState baseline = CanonicalState.withDigestForTest(collision, "{\"x\":1}");
        CanonicalState mutant = CanonicalState.withDigestForTest(collision, "{\"x\":2}");

        StateRelationAnalyzer.Analysis analysis = analyzer.analyze(List.of(baseline), List.of(mutant));

        assertEquals(StateRelation.STATE_INCOMPARABLE, analysis.relation());
        assertTrue(analysis.mutantOnlyWitness().isPresent());
    }

    @Test
    void anIncompleteInputIsUnresolved() {
        assertEquals(
                StateRelation.STATE_UNRESOLVED,
                analyzer.unresolved("truncated-state-output").relation());
    }
}
