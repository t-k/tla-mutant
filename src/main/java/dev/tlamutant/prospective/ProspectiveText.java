package dev.tlamutant.prospective;

import java.util.Objects;

public final class ProspectiveText {
    private ProspectiveText() {}

    public static String requireSafeLine(String value, String field, int maximumCharacters) {
        return requireSafe(value, field, maximumCharacters, false);
    }

    public static String requireSafeMultiline(String value, String field, int maximumCharacters) {
        return requireSafe(value, field, maximumCharacters, true);
    }

    private static String requireSafe(String value, String field, int maximumCharacters, boolean multiline) {
        Objects.requireNonNull(field, "field");
        if (maximumCharacters <= 0) {
            throw new IllegalArgumentException("maximumCharacters must be positive");
        }
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maximumCharacters) {
            throw new IllegalArgumentException(field + " must contain bounded nonblank text");
        }
        if (value.codePoints().anyMatch(codePoint -> isForbidden(codePoint, multiline))) {
            throw new IllegalArgumentException(field + " contains unsafe control or bidirectional text");
        }
        return value;
    }

    private static boolean isForbidden(int codePoint, boolean multiline) {
        if (codePoint == '\n' || codePoint == '\t') {
            return !multiline;
        }
        return Character.isISOControl(codePoint)
                || codePoint == 0x061C
                || codePoint == 0x200E
                || codePoint == 0x200F
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }
}
