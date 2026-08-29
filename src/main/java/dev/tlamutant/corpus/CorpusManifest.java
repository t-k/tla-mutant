package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import java.util.List;

public record CorpusManifest(
        int version, String profile, String repository, String commit, String license, List<Entry> entries) {
    public record Entry(
            String id,
            String domain,
            String rootModule,
            String config,
            List<SourceFile> files,
            List<String> typeInvariants,
            List<String> targetInvariants,
            List<String> adoptedTargetInvariants,
            List<DerivedSourceEdit> derivedSourceEdits,
            ExecutionPolicy.Limits limits,
            Selection selection) {
        public Entry {
            adoptedTargetInvariants =
                    adoptedTargetInvariants == null ? List.of() : List.copyOf(adoptedTargetInvariants);
            derivedSourceEdits = derivedSourceEdits == null ? List.of() : List.copyOf(derivedSourceEdits);
        }

        public Entry(
                String id,
                String domain,
                String rootModule,
                String config,
                List<SourceFile> files,
                List<String> typeInvariants,
                List<String> targetInvariants,
                ExecutionPolicy.Limits limits,
                Selection selection) {
            this(
                    id,
                    domain,
                    rootModule,
                    config,
                    files,
                    typeInvariants,
                    targetInvariants,
                    List.of(),
                    List.of(),
                    limits,
                    selection);
        }
    }

    public record Selection(String cohort, String gcdStratum, String familyCluster, String selectionRationale) {}

    public record SourceFile(String path, String repositoryPath, String sha256, long sizeBytes) {}

    public record DerivedSourceEdit(
            String path,
            String baseSha256,
            long baseSizeBytes,
            long insertionOffset,
            String insertedText,
            String derivedSha256,
            long derivedSizeBytes) {}
}
