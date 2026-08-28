package dev.tlamutant.report;

import java.util.regex.Pattern;

final class MarkdownSanitizer {
    private static final int MAX_LENGTH = 500;
    private static final Pattern ANSI = Pattern.compile("\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])");
    private static final Pattern CONTROL_AND_BIDI = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");

    private MarkdownSanitizer() {}

    static String inline(String input) {
        String normalized = input == null ? "" : ANSI.matcher(input).replaceAll("");
        normalized = CONTROL_AND_BIDI
                .matcher(normalized)
                .replaceAll("")
                .replace('\r', ' ')
                .replace('\n', ' ');
        String bounded = normalized.substring(0, Math.min(normalized.length(), MAX_LENGTH));
        return bounded.replace("\\", "\\\\")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("`", "'")
                .replace("!", "\\!")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    static String tableCell(String input) {
        return inline(input).replace("|", "\\|");
    }
}
