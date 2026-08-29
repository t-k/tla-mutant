package dev.tlamutant.experiment;

import java.util.Objects;

public record GcdTerminalOutcome(String entryId, MutationResult result) {
    public GcdTerminalOutcome {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(result, "result");
        if (!result.family().equals("GCD")) {
            throw new IllegalArgumentException("Terminal GCD outcome must contain a GCD result");
        }
    }
}
