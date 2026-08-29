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
            Set.of("registry", "protocol", "manifest", "policy", "tools", "application", "discovery-receipt", "census");
    public static final Set<String> INTERVENTION_CENSUS_ROLES = Set.of(
            "v10-discovery-receipt",
            "v10-experiment-receipt",
            "v10-protocol",
            "v10-manifest",
            "v10-census",
            "v10-discovery",
            "dossier",
            "review",
            "v11-protocol",
            "v11-manifest",
            "policy",
            "tools",
            "application");
    public static final Set<String> INTERVENTION_EXPERIMENT_ROLES = java.util.stream.Stream.concat(
                    INTERVENTION_CENSUS_ROLES.stream(), java.util.stream.Stream.of("census-receipt", "v11-census"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Pattern PROFILE = Pattern.compile("gcd-prospective-v[0-9]+");

    public ProspectiveReceipt {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Prospective receipt schemaVersion must be 1");
        }
        if (!Set.of("discovery", "experiment", "intervention-census", "intervention-experiment")
                .contains(phase)) {
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
        Set<String> expected =
                switch (phase) {
                    case "discovery" -> DISCOVERY_ROLES;
                    case "experiment" -> EXPERIMENT_ROLES;
                    case "intervention-census" -> INTERVENTION_CENSUS_ROLES;
                    case "intervention-experiment" -> INTERVENTION_EXPERIMENT_ROLES;
                    default -> throw new IllegalStateException("Validated phase is missing a role set");
                };
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
        private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{0,31}");
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
