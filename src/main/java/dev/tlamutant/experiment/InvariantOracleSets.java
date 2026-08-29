package dev.tlamutant.experiment;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record InvariantOracleSets(
        List<String> typeInvariants, List<String> legacyTargetInvariants, List<String> adoptedTargetInvariants) {
    public InvariantOracleSets {
        typeInvariants = List.copyOf(Objects.requireNonNull(typeInvariants, "typeInvariants"));
        legacyTargetInvariants = List.copyOf(Objects.requireNonNull(legacyTargetInvariants, "legacyTargetInvariants"));
        adoptedTargetInvariants =
                List.copyOf(Objects.requireNonNull(adoptedTargetInvariants, "adoptedTargetInvariants"));
        List<String> all = java.util.stream.Stream.of(typeInvariants, legacyTargetInvariants, adoptedTargetInvariants)
                .flatMap(List::stream)
                .toList();
        Set<String> unique = new HashSet<>();
        if (all.isEmpty()
                || all.size() > 64
                || all.stream()
                        .anyMatch(name ->
                                name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}") || !unique.add(name))) {
            throw new IllegalArgumentException("Categorized state invariants must be safe, bounded, and unique");
        }
    }

    public List<String> full() {
        return java.util.stream.Stream.of(typeInvariants, legacyTargetInvariants, adoptedTargetInvariants)
                .flatMap(List::stream)
                .toList();
    }

    public List<String> legacy() {
        return java.util.stream.Stream.of(typeInvariants, legacyTargetInvariants)
                .flatMap(List::stream)
                .toList();
    }
}
