package dev.tlamutant.experiment;

import java.util.Arrays;

public enum OracleMode {
    STATE_INVARIANTS("state-invariants"),
    FULL_PROPERTY("full-property");

    private final String cliName;

    OracleMode(String cliName) {
        this.cliName = cliName;
    }

    public String cliName() {
        return cliName;
    }

    public static OracleMode parse(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.cliName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported oracle mode: " + value));
    }
}
