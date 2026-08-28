package dev.tlamutant.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import tlc2.tool.impl.ModelConfig;

final class StateInvariantConfig {
    private static final Set<String> KEYWORDS =
            Arrays.stream(ModelConfig.ALL_KEYWORDS).collect(Collectors.toUnmodifiableSet());

    private StateInvariantConfig() {}

    static void write(Path source, Path target) throws IOException {
        String input = Files.readString(source);
        StringBuilder output = new StringBuilder(input.length());
        boolean skippingProperty = false;
        for (String line : input.split("(?<=\\n)", -1)) {
            String keyword = keyword(line);
            if ("PROPERTY".equals(keyword) || "PROPERTIES".equals(keyword)) {
                skippingProperty = true;
                continue;
            }
            if (skippingProperty && keyword != null) {
                skippingProperty = false;
            }
            if (!skippingProperty) {
                output.append(line);
            }
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
