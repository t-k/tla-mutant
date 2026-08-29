package dev.tlamutant.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tlc2.tool.impl.ModelConfig;

final class StateInvariantConfig {
    private static final Set<String> KEYWORDS =
            Arrays.stream(ModelConfig.ALL_KEYWORDS).collect(Collectors.toUnmodifiableSet());

    private StateInvariantConfig() {}

    static void write(Path source, Path target) throws IOException {
        write(source, target, null);
    }

    static void write(Path source, Path target, List<String> stateInvariants) throws IOException {
        if (stateInvariants != null
                && (stateInvariants.isEmpty()
                        || stateInvariants.size() > 64
                        || stateInvariants.stream()
                                .anyMatch(name -> name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))
                        || stateInvariants.stream().distinct().count() != stateInvariants.size())) {
            throw new IllegalArgumentException("State invariant allowlist must be safe, bounded, and unique");
        }
        String input = Files.readString(source);
        StringBuilder output = new StringBuilder(input.length());
        boolean skippingRemovedSection = false;
        for (String line : input.split("(?<=\\n)", -1)) {
            String keyword = keyword(line);
            boolean removeInvariantSection =
                    stateInvariants != null && ("INVARIANT".equals(keyword) || "INVARIANTS".equals(keyword));
            if ("PROPERTY".equals(keyword) || "PROPERTIES".equals(keyword) || removeInvariantSection) {
                skippingRemovedSection = true;
                continue;
            }
            if (skippingRemovedSection && keyword != null) {
                skippingRemovedSection = false;
            }
            if (!skippingRemovedSection) {
                output.append(line);
            }
        }
        if (stateInvariants != null) {
            if (!output.isEmpty() && output.charAt(output.length() - 1) != '\n') {
                output.append('\n');
            }
            output.append("INVARIANTS\n");
            stateInvariants.forEach(name -> output.append("  ").append(name).append('\n'));
        }
        Files.writeString(target, output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static String keyword(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.isEmpty() || trimmed.startsWith("\\*") || trimmed.startsWith("(*") || trimmed.startsWith("*)")) {
            return null;
        }
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        String token = trimmed.substring(0, end);
        return KEYWORDS.contains(token) ? token : null;
    }
}
