package dev.tlamutant.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StratifiedManifestValidatorTest {
    private static final ExecutionPolicy.Limits GLOBAL =
            new ExecutionPolicy.Limits(120, 1024, 64, 128, 4096, 100, 2048, 2048, 10_000, 180);

    @Test
    void preservesVersionOneManifestCompatibility() throws Exception {
        CorpusManifest original =
                new ObjectMapper().readValue(Path.of("corpus/manifest.json").toFile(), CorpusManifest.class);

        assertEquals(1, original.version());
        assertNull(original.profile());
        assertTrue(original.entries().stream().allMatch(entry -> entry.selection() == null));
    }

    @Test
    void acceptsTheExactFiveControlTenEnrichedProfile() {
        CorpusManifest manifest = manifest(5, 10);

        assertSame(manifest, StratifiedManifestValidator.validate(manifest, GLOBAL));
    }

    @Test
    void acceptsTheVersionedReselectionAndInstrumentationProfiles() {
        CorpusManifest original = manifest(5, 10);
        for (String profile : List.of("gcd-stratified-v2", "gcd-stratified-v3", "gcd-stratified-v4")) {
            CorpusManifest manifest = new CorpusManifest(
                    original.version(),
                    profile,
                    original.repository(),
                    original.commit(),
                    original.license(),
                    original.entries());

            assertSame(manifest, StratifiedManifestValidator.validate(manifest, GLOBAL));
        }
    }

    @Test
    void rejectsWrongCohortCountsOrTooFewEnrichedStrata() {
        CorpusManifest wrongCounts = manifest(4, 11);
        CorpusManifest tooFewStrata = withSelections(manifest(5, 10).entries().stream()
                .map(entry -> entry.selection().cohort().equals("gcd-enriched")
                        ? withSelection(
                                entry,
                                selection(
                                        "gcd-enriched",
                                        "guarded-admission-positive-control",
                                        entry.selection().familyCluster(),
                                        "one static reason"))
                        : entry)
                .toList());

        assertThrows(IllegalArgumentException.class, () -> StratifiedManifestValidator.validate(wrongCounts, GLOBAL));
        assertThrows(IllegalArgumentException.class, () -> StratifiedManifestValidator.validate(tooFewStrata, GLOBAL));
    }

    @Test
    void rejectsUnknownOrInconsistentSelectionMetadata() {
        CorpusManifest unknownCohort =
                replaceSelection(0, selection("unknown", "control-baseline", "cluster-0", "one static reason"));
        CorpusManifest controlWithEnrichedStratum = replaceSelection(
                0, selection("control", "guarded-admission-positive-control", "cluster-0", "one static reason"));
        CorpusManifest enrichedWithControlStratum =
                replaceSelection(5, selection("gcd-enriched", "control-baseline", "cluster-5", "one static reason"));
        CorpusManifest blankRationale =
                replaceSelection(5, selection("gcd-enriched", "lifecycle-or-pc-ordering", "cluster-5", " "));
        CorpusManifest unsafeCluster = replaceSelection(
                5, selection("gcd-enriched", "lifecycle-or-pc-ordering", "../cluster", "one static reason"));

        for (CorpusManifest invalid : List.of(
                unknownCohort, controlWithEnrichedStratum, enrichedWithControlStratum, blankRationale, unsafeCluster)) {
            assertThrows(IllegalArgumentException.class, () -> StratifiedManifestValidator.validate(invalid, GLOBAL));
        }
    }

    @Test
    void rejectsDuplicateIdsTooFewDomainsAndOversizedRationale() {
        List<CorpusManifest.Entry> duplicateEntries =
                new ArrayList<>(manifest(5, 10).entries());
        duplicateEntries.set(
                1, withId(duplicateEntries.get(1), duplicateEntries.get(0).id()));
        CorpusManifest duplicateIds = withSelections(duplicateEntries);
        CorpusManifest oneDomain = withSelections(manifest(5, 10).entries().stream()
                .map(entry -> withDomain(entry, "one-domain"))
                .toList());
        CorpusManifest oversized = replaceSelection(
                5, selection("gcd-enriched", "lifecycle-or-pc-ordering", "cluster-5", "x".repeat(501)));

        assertThrows(IllegalArgumentException.class, () -> StratifiedManifestValidator.validate(duplicateIds, GLOBAL));
        assertThrows(IllegalArgumentException.class, () -> StratifiedManifestValidator.validate(oneDomain, GLOBAL));
        assertThrows(IllegalArgumentException.class, () -> StratifiedManifestValidator.validate(oversized, GLOBAL));
    }

    @Test
    void rejectsUnboundedOrMisleadingIdentityText() {
        CorpusManifest oversizedId =
                withEntry(0, withId(manifest(5, 10).entries().get(0), "a".repeat(65)));
        CorpusManifest oversizedDomain =
                withEntry(0, withDomain(manifest(5, 10).entries().get(0), "a".repeat(65)));
        CorpusManifest bidiDomain =
                withEntry(0, withDomain(manifest(5, 10).entries().get(0), "domain\u202Ename"));
        CorpusManifest controlRationale = replaceSelection(
                5, selection("gcd-enriched", "lifecycle-or-pc-ordering", "cluster-5", "reason\u001B[31m"));

        for (CorpusManifest invalid : List.of(oversizedId, oversizedDomain, bidiDomain, controlRationale)) {
            assertThrows(IllegalArgumentException.class, () -> StratifiedManifestValidator.validate(invalid, GLOBAL));
        }
    }

    private static CorpusManifest withEntry(int index, CorpusManifest.Entry replacement) {
        List<CorpusManifest.Entry> entries = new ArrayList<>(manifest(5, 10).entries());
        entries.set(index, replacement);
        return withSelections(entries);
    }

    private static CorpusManifest replaceSelection(int index, CorpusManifest.Selection selection) {
        List<CorpusManifest.Entry> entries = new ArrayList<>(manifest(5, 10).entries());
        entries.set(index, withSelection(entries.get(index), selection));
        return withSelections(entries);
    }

    private static CorpusManifest manifest(int controls, int enriched) {
        List<CorpusManifest.Entry> entries = new ArrayList<>();
        for (int index = 0; index < controls; index++) {
            entries.add(entry(
                    "control-" + index,
                    "domain-" + (index % 3),
                    selection("control", "control-baseline", "control-cluster-" + index, "one static reason")));
        }
        List<String> strata = List.of(
                "guarded-admission-positive-control",
                "lifecycle-or-pc-ordering",
                "type-only-gap-prospect",
                "collision-or-capacity");
        for (int index = 0; index < enriched; index++) {
            entries.add(entry(
                    "enriched-" + index,
                    "domain-" + (index % 4),
                    selection(
                            "gcd-enriched",
                            strata.get(index % strata.size()),
                            "enriched-cluster-" + index,
                            "one static reason")));
        }
        return withSelections(entries);
    }

    private static CorpusManifest withSelections(List<CorpusManifest.Entry> entries) {
        return new CorpusManifest(
                2, "gcd-stratified-v1", "https://github.com/tlaplus/Examples", "a".repeat(40), "MIT", entries);
    }

    private static CorpusManifest.Entry entry(String id, String domain, CorpusManifest.Selection selection) {
        return new CorpusManifest.Entry(
                id,
                domain,
                "Spec.tla",
                "Spec.cfg",
                List.of(file("Spec.tla"), file("Spec.cfg")),
                List.of("TypeOK"),
                List.of("Safety"),
                new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90),
                selection);
    }

    private static CorpusManifest.Entry withSelection(CorpusManifest.Entry entry, CorpusManifest.Selection selection) {
        return new CorpusManifest.Entry(
                entry.id(),
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                entry.files(),
                entry.typeInvariants(),
                entry.targetInvariants(),
                entry.limits(),
                selection);
    }

    private static CorpusManifest.Entry withId(CorpusManifest.Entry entry, String id) {
        return new CorpusManifest.Entry(
                id,
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                entry.files(),
                entry.typeInvariants(),
                entry.targetInvariants(),
                entry.limits(),
                entry.selection());
    }

    private static CorpusManifest.Entry withDomain(CorpusManifest.Entry entry, String domain) {
        return new CorpusManifest.Entry(
                entry.id(),
                domain,
                entry.rootModule(),
                entry.config(),
                entry.files(),
                entry.typeInvariants(),
                entry.targetInvariants(),
                entry.limits(),
                entry.selection());
    }

    private static CorpusManifest.Selection selection(String cohort, String stratum, String cluster, String rationale) {
        return new CorpusManifest.Selection(cohort, stratum, cluster, rationale);
    }

    private static CorpusManifest.SourceFile file(String path) {
        return new CorpusManifest.SourceFile(path, "specifications/sample/" + path, "0".repeat(64), 1);
    }
}
