package dev.tlamutant.tlc;

import java.util.Objects;

public record TlcToolEvent(int code, int severity, String body) {
    public TlcToolEvent {
        if (code <= 0 || severity < 0) {
            throw new IllegalArgumentException("Invalid TLC tool event header");
        }
        Objects.requireNonNull(body, "body");
    }
}
