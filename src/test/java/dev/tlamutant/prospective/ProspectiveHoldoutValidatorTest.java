package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.ProspectiveHoldoutValidator;
import dev.tlamutant.corpus.ValidatedCorpusManifest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProspectiveHoldoutValidatorTest {
    private static final ExecutionPolicy.Limits GLOBAL =
            new ExecutionPolicy.Limits(120, 1024, 64, 128, 4096, 100, 2048, 2048, 10_000, 180);
    private static final ExecutionPolicy.Limits ENTRY_LIMITS =
            new ExecutionPolicy.Limits(60, 512, 32, 64, 2048, 50, 1024, 1024, 5_000, 90);

    @Test
    void acceptsTheExactEightEntryHoldout() throws Exception {
        Fixture fixture = fixture();

        ValidatedCorpusManifest validated = ProspectiveHoldoutValidator.validate(
                fixture.manifest(), fixture.registry(), fixture.protocol(), GLOBAL);

        assertEquals(fixture.manifest(), validated.manifest());
        assertEquals("prospective-v10", validated.purpose());
    }

    @Test
    void rejectsHistoricalPathEvenUnderANewEntryId() throws Exception {
        Fixture fixture = fixture();
        CorpusManifest changed = replaceFile(
                fixture.manifest(),
                0,
                0,
                new CorpusManifest.SourceFile("A.tla", "historical/Old.tla", digest('a'), 101));
        ProspectiveProtocol changedProtocol = replaceProtocolEntry(
                fixture.protocol(),
                0,
                withRepositoryPath(fixture.protocol().entries().getFirst(), 0, "historical/Old.tla"));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveHoldoutValidator.validate(changed, fixture.registry(), changedProtocol, GLOBAL));
    }

    @Test
    void rejectsHistoricalBytesEvenUnderANewRepositoryPath() throws Exception {
        Fixture fixture = fixture();
        CorpusManifest changed = replaceFile(
                fixture.manifest(),
                0,
                0,
                new CorpusManifest.SourceFile("A.tla", "prospective/new-name.tla", digest('f'), 77));
        ProspectiveProtocol changedProtocol = replaceProtocolEntry(
                fixture.protocol(),
                0,
                withRepositoryPath(fixture.protocol().entries().getFirst(), 0, "prospective/new-name.tla"));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveHoldoutValidator.validate(changed, fixture.registry(), changedProtocol, GLOBAL));
    }

    @Test
    void rejectsAnyChangeToMembershipRiskOracleOrSourceIdentity() throws Exception {
        Fixture fixture = fixture();
        CorpusManifest removed =
                withEntries(fixture.manifest(), fixture.manifest().entries().subList(0, 7));
        CorpusManifest changedType = replaceEntry(
                fixture.manifest(),
                0,
                withOracles(fixture.manifest().entries().getFirst(), List.of("DifferentType"), List.of("Safety")));
        CorpusManifest withEdit = replaceEntry(
                fixture.manifest(),
                0,
                withIntervention(fixture.manifest().entries().getFirst()));
        ProspectiveProtocol changedRisk = replaceProtocolEntry(
                fixture.protocol(),
                0,
                withRisk(fixture.protocol().entries().getFirst(), ProspectiveProtocol.HIGH_RISK));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveHoldoutValidator.validate(removed, fixture.registry(), fixture.protocol(), GLOBAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveHoldoutValidator.validate(
                        changedType, fixture.registry(), fixture.protocol(), GLOBAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveHoldoutValidator.validate(withEdit, fixture.registry(), fixture.protocol(), GLOBAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveHoldoutValidator.validate(
                        fixture.manifest(), fixture.registry(), changedRisk, GLOBAL));
    }

    @Test
    void rejectsRegistryOrProtocolBindingMismatch() throws Exception {
        Fixture fixture = fixture();
        HistoricalSourceRegistry registry = historicalRegistry("another/Old.tla", 'e', 78);

        assertThrows(
                IllegalArgumentException.class,
                () -> ProspectiveHoldoutValidator.validate(fixture.manifest(), registry, fixture.protocol(), GLOBAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveProtocol(
                        fixture.protocol().schemaVersion(),
                        fixture.protocol().profile(),
                        "https://example.invalid/other",
                        fixture.protocol().commit(),
                        fixture.protocol().standardRiskEntries(),
                        fixture.protocol().highRiskEntries(),
                        fixture.protocol().interventionCap(),
                        fixture.protocol().historicalSet(),
                        fixture.protocol().eligibility(),
                        fixture.protocol().rubric(),
                        fixture.protocol().entries()));
    }

    @Test
    void rejectsUnsafeProtocolTextAndNonExactRubric() throws Exception {
        Fixture fixture = fixture();
        ProspectiveProtocol.Entry first = fixture.protocol().entries().getFirst();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveProtocol.Entry(
                        first.id(),
                        first.domain(),
                        first.riskClass(),
                        first.familyCluster(),
                        first.rootModule(),
                        first.config(),
                        first.repositoryPaths(),
                        first.typeInvariants(),
                        first.legacyTargetInvariants(),
                        first.limits(),
                        "misleading\u202Etext"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectiveProtocol.ReviewRubric(
                        ProspectiveProtocol.REVIEWER_MODE,
                        List.of("exact-predicate"),
                        ProspectiveProtocol.REVIEW_DECISIONS));
    }

    @Test
    void usesTheExactPreregisteredTerminalDecisionVocabulary() {
        assertEquals(
                List.of(
                        "ACCEPT",
                        "REJECT_ALLOWED_STATE",
                        "REJECT_REDUNDANT",
                        "REJECT_NON_INDUCTIVE",
                        "CONFIGURATION_FIX",
                        "NEEDS_DOMAIN_OWNER",
                        "NEEDS_STRENGTHENING"),
                ProspectiveProtocol.REVIEW_DECISIONS);
    }

    @Test
    void permitsAReviewedSafetyOracleWhenTheUpstreamConfigHasNoTypeInvariant() {
        assertDoesNotThrow(() -> new ProspectiveProtocol.Entry(
                "readers-writers",
                "concurrency",
                ProspectiveProtocol.STANDARD_RISK,
                "readers-writers",
                "ReadersWriters.tla",
                "APReadersWriters.cfg",
                List.of(
                        "specifications/ReadersWriters/ReadersWriters.tla",
                        "specifications/ReadersWriters/APReadersWriters.cfg"),
                List.of(),
                List.of("Safety"),
                ENTRY_LIMITS,
                "The upstream config checks Safety only."));
    }

    @Test
    void permitsATypeOracleWhenTheUpstreamConfigHasNoLegacyTargetInvariant() {
        assertDoesNotThrow(() -> new ProspectiveProtocol.Entry(
                "coffee-can",
                "state-machine",
                ProspectiveProtocol.STANDARD_RISK,
                "coffee-can",
                "CoffeeCan.tla",
                "APCoffeeCan.cfg",
                List.of("specifications/CoffeeCan/CoffeeCan.tla", "specifications/CoffeeCan/APCoffeeCan.cfg"),
                List.of("TypeInvariant"),
                List.of(),
                ENTRY_LIMITS,
                "The upstream config checks a type invariant only."));
    }

    private static Fixture fixture() throws Exception {
        HistoricalSourceRegistry registry = historicalRegistry("historical/Old.tla", 'f', 77);
        List<CorpusManifest.Entry> manifestEntries = new ArrayList<>();
        List<ProspectiveProtocol.Entry> protocolEntries = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            String id = "entry-" + index;
            String root = "Spec" + index + ".tla";
            String config = "Spec" + index + ".cfg";
            String rootRepositoryPath = "prospective/" + root;
            String configRepositoryPath = "prospective/" + config;
            String risk = index < 6 ? ProspectiveProtocol.STANDARD_RISK : ProspectiveProtocol.HIGH_RISK;
            String rationale = "Frozen candidate " + index + ".";
            List<CorpusManifest.SourceFile> files = List.of(
                    new CorpusManifest.SourceFile(root, rootRepositoryPath, sha256("root-" + index), 101 + index),
                    new CorpusManifest.SourceFile(config, configRepositoryPath, sha256("config-" + index), 41 + index));
            manifestEntries.add(new CorpusManifest.Entry(
                    id,
                    "domain-" + index,
                    root,
                    config,
                    files,
                    List.of("TypeOK"),
                    List.of("Safety"),
                    List.of(),
                    List.of(),
                    ENTRY_LIMITS,
                    new CorpusManifest.Selection("prospective", risk, "family-" + index, rationale)));
            protocolEntries.add(new ProspectiveProtocol.Entry(
                    id,
                    "domain-" + index,
                    risk,
                    "family-" + index,
                    root,
                    config,
                    List.of(rootRepositoryPath, configRepositoryPath),
                    List.of("TypeOK"),
                    List.of("Safety"),
                    ENTRY_LIMITS,
                    rationale));
        }
        ProspectiveProtocol protocol = new ProspectiveProtocol(
                1,
                "gcd-prospective-v10",
                "https://github.com/tlaplus/Examples",
                "e".repeat(40),
                6,
                2,
                3,
                new ProspectiveProtocol.HistoricalSet(
                        registry.rootPathCount(),
                        registry.rootPathSetSha256(),
                        registry.sourceCount(),
                        registry.sourceSetSha256()),
                ProspectiveProtocol.requiredEligibility(),
                ProspectiveProtocol.requiredReviewRubric(),
                protocolEntries);
        CorpusManifest manifest = new CorpusManifest(
                4, protocol.profile(), protocol.repository(), protocol.commit(), "MIT", manifestEntries);
        return new Fixture(registry, protocol, manifest);
    }

    private static HistoricalSourceRegistry historicalRegistry(String path, char digest, long size) throws Exception {
        HistoricalSourceRegistry.SourceIdentity source = new HistoricalSourceRegistry.SourceIdentity(
                "corpus/old.json", "old-entry", "root", path, digest(digest), size);
        String rootDigest = sha256(path + "\n");
        String sourceDigest =
                sha256(path + "\t" + digest(digest) + "\t" + size + "\tcorpus/old.json\told-entry\troot\n");
        return new HistoricalSourceRegistry(
                1,
                "https://github.com/tlaplus/Examples",
                "e".repeat(40),
                1,
                rootDigest,
                1,
                sourceDigest,
                List.of(source));
    }

    private static CorpusManifest replaceFile(
            CorpusManifest manifest, int entryIndex, int fileIndex, CorpusManifest.SourceFile replacement) {
        CorpusManifest.Entry original = manifest.entries().get(entryIndex);
        List<CorpusManifest.SourceFile> files = new ArrayList<>(original.files());
        files.set(fileIndex, replacement);
        CorpusManifest.Entry changed = new CorpusManifest.Entry(
                original.id(),
                original.domain(),
                original.rootModule(),
                original.config(),
                files,
                original.typeInvariants(),
                original.targetInvariants(),
                original.adoptedTargetInvariants(),
                original.derivedSourceEdits(),
                original.limits(),
                original.selection());
        return replaceEntry(manifest, entryIndex, changed);
    }

    private static CorpusManifest replaceEntry(CorpusManifest manifest, int index, CorpusManifest.Entry replacement) {
        List<CorpusManifest.Entry> entries = new ArrayList<>(manifest.entries());
        entries.set(index, replacement);
        return withEntries(manifest, entries);
    }

    private static CorpusManifest withEntries(CorpusManifest manifest, List<CorpusManifest.Entry> entries) {
        return new CorpusManifest(
                manifest.version(),
                manifest.profile(),
                manifest.repository(),
                manifest.commit(),
                manifest.license(),
                entries);
    }

    private static CorpusManifest.Entry withOracles(
            CorpusManifest.Entry entry, List<String> type, List<String> targets) {
        return new CorpusManifest.Entry(
                entry.id(),
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                entry.files(),
                type,
                targets,
                entry.adoptedTargetInvariants(),
                entry.derivedSourceEdits(),
                entry.limits(),
                entry.selection());
    }

    private static CorpusManifest.Entry withIntervention(CorpusManifest.Entry entry) throws Exception {
        CorpusManifest.SourceFile root = entry.files().getFirst();
        String insertedText = "\nReviewedInvariant == TRUE\n";
        CorpusManifest.DerivedSourceEdit edit = new CorpusManifest.DerivedSourceEdit(
                entry.rootModule(),
                "ReviewedInvariant",
                root.sha256(),
                root.sizeBytes(),
                root.sizeBytes(),
                insertedText,
                sha256("derived-source"),
                root.sizeBytes() + insertedText.getBytes(StandardCharsets.UTF_8).length);
        return new CorpusManifest.Entry(
                entry.id(),
                entry.domain(),
                entry.rootModule(),
                entry.config(),
                entry.files(),
                entry.typeInvariants(),
                entry.targetInvariants(),
                List.of("ReviewedInvariant"),
                List.of(edit),
                entry.limits(),
                entry.selection());
    }

    private static ProspectiveProtocol replaceProtocolEntry(
            ProspectiveProtocol protocol, int index, ProspectiveProtocol.Entry replacement) {
        List<ProspectiveProtocol.Entry> entries = new ArrayList<>(protocol.entries());
        entries.set(index, replacement);
        int standard = (int) entries.stream()
                .filter(entry -> entry.riskClass().equals(ProspectiveProtocol.STANDARD_RISK))
                .count();
        int high = entries.size() - standard;
        return new ProspectiveProtocol(
                protocol.schemaVersion(),
                protocol.profile(),
                protocol.repository(),
                protocol.commit(),
                standard,
                high,
                protocol.interventionCap(),
                protocol.historicalSet(),
                protocol.eligibility(),
                protocol.rubric(),
                entries);
    }

    private static ProspectiveProtocol.Entry withRisk(ProspectiveProtocol.Entry entry, String risk) {
        return new ProspectiveProtocol.Entry(
                entry.id(),
                entry.domain(),
                risk,
                entry.familyCluster(),
                entry.rootModule(),
                entry.config(),
                entry.repositoryPaths(),
                entry.typeInvariants(),
                entry.legacyTargetInvariants(),
                entry.limits(),
                entry.rationale());
    }

    private static ProspectiveProtocol.Entry withRepositoryPath(
            ProspectiveProtocol.Entry entry, int index, String replacement) {
        List<String> repositoryPaths = new ArrayList<>(entry.repositoryPaths());
        repositoryPaths.set(index, replacement);
        return new ProspectiveProtocol.Entry(
                entry.id(),
                entry.domain(),
                entry.riskClass(),
                entry.familyCluster(),
                entry.rootModule(),
                entry.config(),
                repositoryPaths,
                entry.typeInvariants(),
                entry.legacyTargetInvariants(),
                entry.limits(),
                entry.rationale());
    }

    private static String digest(char character) {
        return String.valueOf(character).repeat(64);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Fixture(HistoricalSourceRegistry registry, ProspectiveProtocol protocol, CorpusManifest manifest) {}
}
