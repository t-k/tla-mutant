package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class StratifiedManifestValidator {
    private static final Set<String> PROFILES =
            Set.of("gcd-stratified-v1", "gcd-stratified-v2", "gcd-stratified-v3", "gcd-stratified-v4");
    private static final String CONTROL = "control";
    private static final String ENRICHED = "gcd-enriched";
    private static final String CONTROL_STRATUM = "control-baseline";
    private static final Set<String> ENRICHED_STRATA = Set.of(
            "guarded-admission-positive-control",
            "lifecycle-or-pc-ordering",
            "type-only-gap-prospect",
            "collision-or-capacity");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private StratifiedManifestValidator() {}

    public static CorpusManifest validate(CorpusManifest manifest, ExecutionPolicy.Limits globalLimits) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(globalLimits, "globalLimits");
        if (manifest.version() != 2 || !isApprovedProfile(manifest.profile())) {
            throw new IllegalArgumentException("Expected the approved GCD-stratified manifest profile");
        }
        if (manifest.entries() == null || manifest.entries().size() != 15) {
            throw new IllegalArgumentException("The GCD-stratified corpus must contain exactly 15 entries");
        }

        int controls = 0;
        int enriched = 0;
        Set<String> ids = new HashSet<>();
        Set<String> domains = new HashSet<>();
        Set<String> enrichedStrata = new HashSet<>();
        for (CorpusManifest.Entry entry : manifest.entries()) {
            ManifestValidator.validateEntry(entry, globalLimits);
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Corpus entry IDs must be unique");
            }
            requireIdentifier(entry.domain(), "domain");
            domains.add(entry.domain());
            CorpusManifest.Selection selection = Objects.requireNonNull(entry.selection(), "selection");
            requireIdentifier(selection.familyCluster(), "familyCluster");
            requireRationale(selection.selectionRationale());
            if (CONTROL.equals(selection.cohort())) {
                controls++;
                if (!CONTROL_STRATUM.equals(selection.gcdStratum())) {
                    throw new IllegalArgumentException("Control entries must use the control stratum");
                }
            } else if (ENRICHED.equals(selection.cohort())) {
                enriched++;
                if (!ENRICHED_STRATA.contains(selection.gcdStratum())) {
                    throw new IllegalArgumentException("Enriched entries must use an approved enriched stratum");
                }
                enrichedStrata.add(selection.gcdStratum());
            } else {
                throw new IllegalArgumentException("Unknown corpus cohort");
            }
        }
        if (controls != 5 || enriched != 10) {
            throw new IllegalArgumentException("The corpus must contain five controls and ten enriched entries");
        }
        if (domains.size() < 3) {
            throw new IllegalArgumentException("The corpus must cover at least three domains");
        }
        if (enrichedStrata.size() < 3) {
            throw new IllegalArgumentException("The enriched cohort must cover at least three strata");
        }
        return manifest;
    }

    static boolean isApprovedProfile(String profile) {
        return PROFILES.contains(profile);
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a safe bounded identifier");
        }
    }

    private static void requireRationale(String rationale) {
        if (rationale == null
                || rationale.isBlank()
                || rationale.length() > 500
                || rationale.codePoints().anyMatch(StratifiedManifestValidator::isMisleadingCodePoint)) {
            throw new IllegalArgumentException("selectionRationale must contain 1 to 500 characters");
        }
    }

    private static boolean isMisleadingCodePoint(int codePoint) {
        return Character.isISOControl(codePoint)
                || codePoint == 0x061C
                || codePoint == 0x200E
                || codePoint == 0x200F
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }
}
