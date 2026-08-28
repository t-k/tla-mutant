package dev.tlamutant.state;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record CanonicalState(String sha256, String canonicalValue) {
    public CanonicalState {
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(canonicalValue, "canonicalValue");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hexadecimal characters");
        }
        if (canonicalValue.isEmpty()) {
            throw new IllegalArgumentException("canonicalValue must not be empty");
        }
    }

    public static CanonicalState of(String canonicalValue) {
        Objects.requireNonNull(canonicalValue, "canonicalValue");
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return new CanonicalState(HexFormat.of().formatHex(digest), canonicalValue);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static CanonicalState withDigestForTest(String sha256, String canonicalValue) {
        return new CanonicalState(sha256, canonicalValue);
    }
}
