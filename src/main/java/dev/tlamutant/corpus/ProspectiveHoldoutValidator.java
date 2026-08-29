package dev.tlamutant.corpus;

import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.prospective.HistoricalSourceRegistry;
import dev.tlamutant.prospective.ProspectiveProtocol;
import dev.tlamutant.security.SafeRelativePath;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProspectiveHoldoutValidator {
    private static final String PROFILE = "gcd-prospective-v10";

    private ProspectiveHoldoutValidator() {}

    public static ValidatedCorpusManifest validate(
            CorpusManifest manifest,
            HistoricalSourceRegistry registry,
            ProspectiveProtocol protocol,
            ExecutionPolicy.Limits globalLimits) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(globalLimits, "globalLimits");
        validateTopLevel(manifest, registry, protocol);
        validateHistoricalBinding(registry, protocol.historicalSet());
        if (protocol.standardRiskEntries() != 6
                || protocol.highRiskEntries() != 2
                || protocol.interventionCap() != 3
                || protocol.entries().size() != 8
                || manifest.entries() == null
                || manifest.entries().size() != 8) {
            throw new IllegalArgumentException("Prospective v10 requires the frozen 6+2 eight-entry protocol");
        }

        Set<String> historicalPaths = registry.sources().stream()
                .map(HistoricalSourceRegistry.SourceIdentity::repositoryPath)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ContentIdentity> historicalContents = registry.sources().stream()
                .map(source -> new ContentIdentity(source.sha256(), source.sizeBytes()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        for (int index = 0; index < manifest.entries().size(); index++) {
            CorpusManifest.Entry entry = manifest.entries().get(index);
            ProspectiveProtocol.Entry expected = protocol.entries().get(index);
            ManifestValidator.validateEntry(entry, globalLimits);
            validateEntry(entry, expected);
            for (CorpusManifest.SourceFile source : entry.files()) {
                if (historicalPaths.contains(source.repositoryPath())
                        || historicalContents.contains(new ContentIdentity(source.sha256(), source.sizeBytes()))) {
                    throw new IllegalArgumentException("Prospective source overlaps the historical source registry");
                }
            }
        }
        return new ValidatedCorpusManifest(manifest, "prospective-v10");
    }

    private static void validateTopLevel(
            CorpusManifest manifest, HistoricalSourceRegistry registry, ProspectiveProtocol protocol) {
        if (manifest.version() != 4
                || !PROFILE.equals(manifest.profile())
                || !PROFILE.equals(protocol.profile())
                || !protocol.repository().equals(manifest.repository())
                || !protocol.commit().equals(manifest.commit())
                || !"MIT".equals(manifest.license())
                || !registry.repository().equals(protocol.repository())
                || !registry.commit().equals(protocol.commit())) {
            throw new IllegalArgumentException("Prospective provenance differs from the frozen protocol");
        }
    }

    private static void validateHistoricalBinding(
            HistoricalSourceRegistry registry, ProspectiveProtocol.HistoricalSet historicalSet) {
        if (historicalSet.rootPathCount() != registry.rootPathCount()
                || !historicalSet.rootPathSetSha256().equals(registry.canonicalRootPathSetSha256())
                || historicalSet.sourceCount() != registry.sourceCount()
                || !historicalSet.sourceSetSha256().equals(registry.canonicalSourceSetSha256())) {
            throw new IllegalArgumentException("Prospective protocol does not bind the historical registry");
        }
    }

    private static void validateEntry(CorpusManifest.Entry entry, ProspectiveProtocol.Entry expected) {
        List<String> repositoryPaths = entry.files().stream()
                .map(CorpusManifest.SourceFile::repositoryPath)
                .toList();
        if (!entry.id().equals(expected.id())
                || !entry.domain().equals(expected.domain())
                || !entry.rootModule().equals(expected.rootModule())
                || !entry.config().equals(expected.config())
                || !repositoryPaths.equals(expected.repositoryPaths())
                || !entry.typeInvariants().equals(expected.typeInvariants())
                || !entry.targetInvariants().equals(expected.legacyTargetInvariants())
                || !entry.limits().equals(expected.limits())
                || !entry.adoptedTargetInvariants().isEmpty()
                || !entry.derivedSourceEdits().isEmpty()) {
            throw new IllegalArgumentException("Prospective manifest entry differs from the frozen protocol");
        }
        CorpusManifest.Selection selection = Objects.requireNonNull(entry.selection(), "selection");
        SafeRelativePath familyCluster = SafeRelativePath.parse(selection.familyCluster());
        if (familyCluster.value().contains("/")
                || !selection.cohort().equals("prospective")
                || !selection.gcdStratum().equals(expected.riskClass())
                || !selection.familyCluster().equals(expected.familyCluster())
                || !selection.selectionRationale().equals(expected.rationale())) {
            throw new IllegalArgumentException("Prospective selection metadata differs from the frozen protocol");
        }
        Set<String> localPaths = new HashSet<>();
        entry.files().forEach(file -> localPaths.add(file.path()));
        if (!localPaths.contains(entry.rootModule()) || !localPaths.contains(entry.config())) {
            throw new IllegalArgumentException("Prospective root and config are not declared files");
        }
    }

    private record ContentIdentity(String sha256, long sizeBytes) {}
}
