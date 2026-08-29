package dev.tlamutant.prospective;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record ProspectiveReceipt(int schemaVersion, String phase, String profile, List<Artifact> artifacts) {
    public static final Set<String> DISCOVERY_ROLES =
            Set.of("registry", "protocol", "manifest", "policy", "tools", "application");
    public static final Set<String> EXPERIMENT_ROLES =
            Set.of("registry", "protocol", "manifest", "policy", "tools", "application", "census");
    private static final Pattern PROFILE = Pattern.compile("gcd-prospective-v[0-9]+");

    public ProspectiveReceipt {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Prospective receipt schemaVersion must be 1");
        }
        if (!Set.of("discovery", "experiment").contains(phase)) {
            throw new IllegalArgumentException("Prospective receipt phase is unknown");
        }
        if (profile == null || !PROFILE.matcher(profile).matches()) {
            throw new IllegalArgumentException("Prospective receipt profile is invalid");
        }
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        Set<String> roles = new HashSet<>();
        for (Artifact artifact : artifacts) {
            Objects.requireNonNull(artifact, "artifact");
            if (!roles.add(artifact.role())) {
                throw new IllegalArgumentException("Prospective receipt artifact roles must be unique");
            }
        }
        Set<String> expected = phase.equals("discovery") ? DISCOVERY_ROLES : EXPERIMENT_ROLES;
        if (!roles.equals(expected)) {
            throw new IllegalArgumentException("Prospective receipt does not contain the exact phase role set");
        }
    }

    public Artifact artifact(String role) {
        return artifacts.stream()
                .filter(artifact -> artifact.role().equals(role))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prospective receipt role is missing: " + role));
    }

    public record Artifact(String role, String sha256, long sizeBytes) {
        private static final Pattern ROLE = Pattern.compile("[a-z][a-z-]{0,31}");
        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        public Artifact {
            if (role == null || !ROLE.matcher(role).matches()) {
                throw new IllegalArgumentException("Prospective receipt role is invalid");
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("Prospective receipt SHA-256 is invalid");
            }
            if (sizeBytes <= 0) {
                throw new IllegalArgumentException("Prospective receipt sizeBytes must be positive");
            }
        }
    }
}
