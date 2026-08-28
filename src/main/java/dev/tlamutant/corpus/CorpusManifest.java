package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import java.util.List;

public record CorpusManifest(int version, String repository, String commit, String license, List<Entry> entries) {
    public record Entry(
            String id,
            String domain,
            String rootModule,
            String config,
            List<SourceFile> files,
            List<String> typeInvariants,
            List<String> targetInvariants,
            ExecutionPolicy.Limits limits) {}

    public record SourceFile(String path, String sha256, long sizeBytes) {}
}
