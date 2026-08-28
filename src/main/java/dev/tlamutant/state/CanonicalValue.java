package dev.tlamutant.state;

import java.util.Objects;

public record CanonicalValue(String kind, String tlaText) {
    public CanonicalValue {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tlaText, "tlaText");
        if (kind.isBlank() || tlaText.isEmpty()) {
            throw new IllegalArgumentException("Canonical values require a kind and value text");
        }
    }

    public String identityText() {
        return kind.length() + ":" + kind + ":" + tlaText.length() + ":" + tlaText;
    }
}
