package dev.tlamutant.experiment;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ExperimentReport(
        int schemaVersion,
        String module,
        String config,
        String reviewStatus,
        int generatedMutants,
        int evaluatedMutants,
        int gapCandidates,
        List<MutationResult> results,
        @JsonInclude(JsonInclude.Include.NON_NULL) String oracleMode,
        @JsonInclude(JsonInclude.Include.NON_NULL) ExperimentInputIdentity inputs) {
    public ExperimentReport {
        results = List.copyOf(results);
        if (schemaVersion == 3
                && (!OracleMode.FULL_PROPERTY.cliName().equals(oracleMode)
                        || inputs == null
                        || inputs.modelFiles().isEmpty()
                        || results.stream().anyMatch(result -> result.evidence() == null))) {
            throw new IllegalArgumentException("Full-property reports require mode, inputs, and mutation evidence");
        }
        if (schemaVersion != 3 && (oracleMode != null || inputs != null)) {
            throw new IllegalArgumentException("Legacy experiment reports cannot contain full-property evidence");
        }
        if (schemaVersion == 3) {
            validateFullPropertyReport(
                    module, config, generatedMutants, evaluatedMutants, gapCandidates, results, inputs);
        }
    }

    public ExperimentReport(
            int schemaVersion,
            String module,
            String config,
            String reviewStatus,
            int generatedMutants,
            int evaluatedMutants,
            int gapCandidates,
            List<MutationResult> results) {
        this(
                schemaVersion,
                module,
                config,
                reviewStatus,
                generatedMutants,
                evaluatedMutants,
                gapCandidates,
                results,
                null,
                null);
    }

    private static void validateFullPropertyReport(
            String module,
            String config,
            int generated,
            int evaluated,
            int survivors,
            List<MutationResult> results,
            ExperimentInputIdentity inputs) {
        if (generated != results.size()
                || evaluated < 0
                || evaluated > generated
                || survivors < 0
                || survivors > evaluated
                || results.stream().map(MutationResult::id).distinct().count() != results.size()) {
            throw new IllegalArgumentException("Full-property report totals or candidate identities are inconsistent");
        }
        ExperimentInputIdentity.Artifact moduleIdentity = modelIdentity(inputs, module);
        ExperimentInputIdentity.Artifact configIdentity = modelIdentity(inputs, config);
        if (!inputs.module().equals(moduleIdentity)
                || !inputs.config().equals(configIdentity)
                || results.stream()
                        .anyMatch(result -> !result.evidence()
                                        .sourceSha256()
                                        .equals(inputs.module().sha256())
                                || !result.definition().equals(result.evidence().definition())
                                || !result.original()
                                        .equals(result.evidence().patch().expected())
                                || !result.replacement()
                                        .equals(result.evidence().patch().replacement()))) {
            throw new IllegalArgumentException("Full-property report evidence does not match its frozen inputs");
        }
    }

    private static ExperimentInputIdentity.Artifact modelIdentity(ExperimentInputIdentity inputs, String path) {
        return inputs.modelFiles().stream()
                .filter(file -> file.path().equals(path))
                .map(ExperimentInputIdentity.ModelFile::identity)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Full-property report omits an executed model file"));
    }
}
