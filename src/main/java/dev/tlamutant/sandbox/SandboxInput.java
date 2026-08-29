package dev.tlamutant.sandbox;

import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.DerivedSourceEditValidator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SandboxInput(
        List<String> flatStateInvariants,
        List<String> typeInvariants,
        List<String> legacyTargetInvariants,
        List<String> adoptedTargetInvariants,
        List<CorpusManifest.DerivedSourceEdit> derivedSourceEdits) {
    private static final int MAX_INVARIANTS = 64;

    public SandboxInput {
        flatStateInvariants = List.copyOf(Objects.requireNonNull(flatStateInvariants, "flatStateInvariants"));
        typeInvariants = List.copyOf(Objects.requireNonNull(typeInvariants, "typeInvariants"));
        legacyTargetInvariants = List.copyOf(Objects.requireNonNull(legacyTargetInvariants, "legacyTargetInvariants"));
        adoptedTargetInvariants =
                List.copyOf(Objects.requireNonNull(adoptedTargetInvariants, "adoptedTargetInvariants"));
        derivedSourceEdits = DerivedSourceEditValidator.validateList(derivedSourceEdits);
        if (!flatStateInvariants.isEmpty()
                && (!typeInvariants.isEmpty()
                        || !legacyTargetInvariants.isEmpty()
                        || !adoptedTargetInvariants.isEmpty())) {
            throw new IllegalArgumentException("Flat and categorized invariant arguments cannot be mixed");
        }
        validateInvariantNames(flatStateInvariants);
        List<String> categorized = java.util.stream.Stream.of(
                        typeInvariants, legacyTargetInvariants, adoptedTargetInvariants)
                .flatMap(List::stream)
                .toList();
        validateInvariantNames(categorized);
    }

    public static SandboxInput legacy(List<String> stateInvariants) {
        return new SandboxInput(stateInvariants, List.of(), List.of(), List.of(), List.of());
    }

    public static SandboxInput attributed(
            List<String> typeInvariants,
            List<String> legacyTargetInvariants,
            List<String> adoptedTargetInvariants,
            List<CorpusManifest.DerivedSourceEdit> derivedSourceEdits) {
        return new SandboxInput(
                List.of(), typeInvariants, legacyTargetInvariants, adoptedTargetInvariants, derivedSourceEdits);
    }

    public static SandboxInput census(List<CorpusManifest.DerivedSourceEdit> derivedSourceEdits) {
        return new SandboxInput(List.of(), List.of(), List.of(), List.of(), derivedSourceEdits);
    }

    boolean hasInvariants() {
        return !flatStateInvariants.isEmpty()
                || !typeInvariants.isEmpty()
                || !legacyTargetInvariants.isEmpty()
                || !adoptedTargetInvariants.isEmpty();
    }

    private static void validateInvariantNames(List<String> invariants) {
        Set<String> unique = new HashSet<>();
        if (invariants.size() > MAX_INVARIANTS
                || invariants.stream()
                        .anyMatch(name ->
                                name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}") || !unique.add(name))) {
            throw new IllegalArgumentException("State-invariant arguments must be safe, bounded, and unique");
        }
    }
}
