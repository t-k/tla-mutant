package dev.tlamutant.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
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
        assertEquals("gcd-stratified-v3", manifest.profile());
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
}
