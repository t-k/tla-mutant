package dev.tlamutant.corpus;

import java.util.Objects;

public final class ValidatedCorpusManifest {
    private final CorpusManifest manifest;
    private final String purpose;

    ValidatedCorpusManifest(CorpusManifest manifest, String purpose) {
        this.manifest = freeze(Objects.requireNonNull(manifest, "manifest"));
        this.purpose = Objects.requireNonNull(purpose, "purpose");
    }

    public CorpusManifest manifest() {
        return manifest;
    }

    public String purpose() {
        return purpose;
    }

    private static CorpusManifest freeze(CorpusManifest source) {
        return new CorpusManifest(
                source.version(),
                source.profile(),
                source.repository(),
                source.commit(),
                source.license(),
                source.entries().stream()
                        .map(ValidatedCorpusManifest::freezeEntry)
                        .toList());
    }

    private static CorpusManifest.Entry freezeEntry(CorpusManifest.Entry entry) {
        return new CorpusManifest.Entry(
                entry.id(),
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                java.util.List.copyOf(entry.files()),
                java.util.List.copyOf(entry.typeInvariants()),
                java.util.List.copyOf(entry.targetInvariants()),
                java.util.List.copyOf(entry.adoptedTargetInvariants()),
                java.util.List.copyOf(entry.derivedSourceEdits()),
                entry.limits(),
                entry.selection());
    }
}
