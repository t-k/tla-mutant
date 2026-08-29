package dev.tlamutant.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.security.Digests;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class StratifiedManifestIntegrationTest {
    private static final List<String> APPROVED_IDS = List.of(
            "channel",
            "nbacc-ray97",
            "barrier",
            "token-ring",
            "asynchronous-termination-detection",
            "prisoner-single-switch",
            "dining-philosophers",
            "simple-allocator",
            "voucher-life-cycle",
            "tcommit",
            "missionaries-and-cannibals",
            "cigarette-smokers",
            "glowing-raccoon-clean",
            "cf1s-folklore",
            "bcast-folklore");
    private static final ExecutionPolicy.Limits GLOBAL = new ExecutionPolicy.Limits(
            120, 1_073_741_824, 128, 256, 67_108_864, 4096, 1_048_576, 1_048_576, 1_000_000, 180);

    @Test
    void pinsTheExactApprovedFiveControlTenEnrichedCorpus() throws Exception {
        CorpusManifest manifest = new ObjectMapper()
                .readValue(Path.of("corpus/gcd-stratified-manifest.json").toFile(), CorpusManifest.class);

        assertEquals(2, manifest.version());
        assertEquals("gcd-stratified-v8", manifest.profile());
        assertEquals("ed3cc4c35f7001d7cec08a22dcd698dae7570dc3", manifest.commit());
        assertEquals(
                APPROVED_IDS,
                manifest.entries().stream().map(CorpusManifest.Entry::id).toList());
        assertEquals(
                5,
                manifest.entries().stream()
                        .filter(entry -> entry.selection().cohort().equals("control"))
                        .count());
        assertEquals(
                10,
                manifest.entries().stream()
                        .filter(entry -> entry.selection().cohort().equals("gcd-enriched"))
                        .count());
        assertTrue(manifest.entries().stream()
                        .map(CorpusManifest.Entry::domain)
                        .distinct()
                        .count()
                >= 3);
        assertTrue(manifest.entries().stream()
                        .filter(entry -> entry.selection().cohort().equals("gcd-enriched"))
                        .map(entry -> entry.selection().gcdStratum())
                        .distinct()
                        .count()
                >= 3);
        for (CorpusManifest.Entry entry : manifest.entries()) {
            Set<String> repositoryPaths = new HashSet<>();
            for (CorpusManifest.SourceFile file : entry.files()) {
                assertTrue(file.sha256().matches("[0-9a-f]{64}"));
                assertTrue(file.sizeBytes() > 0);
                assertTrue(repositoryPaths.add(file.repositoryPath()));
            }
        }
        assertSame(manifest, StratifiedManifestValidator.validate(manifest, GLOBAL));
    }

    @Test
    void missionGoalPredicateIsNotRegisteredAsAStateInvariantOracle() throws Exception {
        CorpusManifest manifest = new ObjectMapper()
                .readValue(Path.of("corpus/gcd-stratified-manifest.json").toFile(), CorpusManifest.class);
        CorpusManifest.Entry mission = manifest.entries().stream()
                .filter(entry -> entry.id().equals("missionaries-and-cannibals"))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("TypeOK"), mission.typeInvariants());
        assertTrue(mission.targetInvariants().isEmpty());
    }

    @Test
    void preservesFrozenV8AndChangesOnlyTheReviewedSimpleAllocatorInterventionInV9() throws Exception {
        assertEquals(
                "1caeb7734ea814e0d9d2e007ae0711f7fa9dcc332e2cce50b7fb780b08e5ec7e",
                Digests.sha256(Path.of("corpus/gcd-stratified-manifest.json"), 1_000_000));
        assertEquals(
                "6de7c400622557a545a82c3db0d29b20f304ab2e0c47ec29eddfea2e9a72df95",
                Digests.sha256(Path.of("docs/experiments/gcd-stratified-census.json"), 10_000_000));
        assertEquals(
                "12de6c80837a4871ea09a63173b88bbb6f1bc7c2c4b0826f6f35084230dcfd5e",
                Digests.sha256(Path.of("docs/experiments/gcd-stratified.json"), 10_000_000));
        assertEquals(
                "43ce8e7fa8f5867d4024d8ea9e44df95ce767b6a4aa657ad46ae8743dffc5f7a",
                Digests.sha256(Path.of("docs/experiments/gcd-stratified.md"), 10_000_000));
        ObjectMapper mapper = new ObjectMapper();
        CorpusManifest v8 =
                mapper.readValue(Path.of("corpus/gcd-stratified-manifest.json").toFile(), CorpusManifest.class);
        CorpusManifest v9 = mapper.readValue(
                Path.of("corpus/gcd-stratified-manifest-v9.json").toFile(), CorpusManifest.class);

        assertEquals(3, v9.version());
        assertEquals("gcd-stratified-v9", v9.profile());
        assertEquals(v8.repository(), v9.repository());
        assertEquals(v8.commit(), v9.commit());
        assertEquals(v8.license(), v9.license());
        assertEquals(
                APPROVED_IDS,
                v9.entries().stream().map(CorpusManifest.Entry::id).toList());
        for (int index = 0; index < v8.entries().size(); index++) {
            CorpusManifest.Entry before = v8.entries().get(index);
            CorpusManifest.Entry after = v9.entries().get(index);
            assertEquals(before.id(), after.id());
            assertEquals(before.domain(), after.domain());
            assertEquals(before.rootModule(), after.rootModule());
            assertEquals(before.config(), after.config());
            assertEquals(before.files(), after.files());
            assertEquals(before.typeInvariants(), after.typeInvariants());
            assertEquals(before.targetInvariants(), after.targetInvariants());
            assertEquals(before.limits(), after.limits());
            assertEquals(before.selection(), after.selection());
            if (!after.id().equals("simple-allocator")) {
                assertTrue(after.adoptedTargetInvariants().isEmpty());
                assertTrue(after.derivedSourceEdits().isEmpty());
            }
        }
        CorpusManifest.Entry simple = v9.entries().stream()
                .filter(entry -> entry.id().equals("simple-allocator"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("NoRequestAllocationOverlap"), simple.adoptedTargetInvariants());
        assertEquals(1, simple.derivedSourceEdits().size());
        CorpusManifest.DerivedSourceEdit edit = simple.derivedSourceEdits().getFirst();
        assertEquals("SimpleAllocator.tla", edit.path());
        assertEquals("NoRequestAllocationOverlap", edit.definedOperator());
        assertEquals("39180889fb44ea7cbba47e97c2fa727a54c43f8f7ff983caf61d67eb0a2db0e3", edit.baseSha256());
        assertEquals(4494, edit.baseSizeBytes());
        assertEquals(2999, edit.insertionOffset());
        assertEquals(
                "NoRequestAllocationOverlap ==\n  \\A c \\in Clients : unsat[c] \\cap alloc[c] = {}\n\n",
                edit.insertedText());
        assertEquals("020b1ee89ca9c54925f46b080a7421bbe1497d4a60d5cb0df4d849dc783381d3", edit.derivedSha256());
        assertEquals(4574, edit.derivedSizeBytes());
        assertSame(v9, StratifiedManifestValidator.validate(v9, GLOBAL));
        CorpusFetcher.validateManifest(v9);
    }
}
