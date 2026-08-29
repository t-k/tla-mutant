package dev.tlamutant.corpus;

import java.util.Objects;

public final class ValidatedCorpusManifest {
    private final CorpusManifest manifest;
    private final String purpose;

    ValidatedCorpusManifest(CorpusManifest manifest, String purpose) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
    }

    public CorpusManifest manifest() {
        return manifest;
    }

    public String purpose() {
        return purpose;
    }
}
