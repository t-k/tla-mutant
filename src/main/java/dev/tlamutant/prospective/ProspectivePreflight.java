package dev.tlamutant.prospective;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.ProspectiveHoldoutValidator;
import dev.tlamutant.corpus.ValidatedCorpusManifest;
import dev.tlamutant.security.BoundedFileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProspectivePreflight {
    private static final long MAXIMUM_RECEIPT_BYTES = 1024L * 1024;
    private static final long MAXIMUM_REGISTRY_BYTES = 16L * 1024 * 1024;
    private static final long MAXIMUM_PROTOCOL_BYTES = 2L * 1024 * 1024;
    private static final long MAXIMUM_MANIFEST_BYTES = 8L * 1024 * 1024;
    private static final long MAXIMUM_POLICY_BYTES = 1024L * 1024;
    private static final long MAXIMUM_TOOLS_BYTES = 128L * 1024 * 1024;
    private static final long MAXIMUM_APPLICATION_BYTES = 128L * 1024 * 1024;
    private static final long MAXIMUM_CENSUS_BYTES = 128L * 1024 * 1024;

    private final ObjectMapper mapper = new ObjectMapper();

    public ValidatedDiscoveryInputs validateDiscovery(DiscoveryPaths paths) throws IOException {
        Objects.requireNonNull(paths, "paths");
        return validate(
                "discovery",
                paths.receipt(),
                paths.registry(),
                paths.protocol(),
                paths.manifest(),
                paths.policy(),
                paths.tools(),
                paths.application(),
                null,
                paths.cacheDirectory(),
                paths.outputs());
    }

    public ValidatedExperimentInputs validateExperiment(ExperimentPaths paths) throws IOException {
        Objects.requireNonNull(paths, "paths");
        ValidatedDiscoveryInputs validated = validate(
                "experiment",
                paths.receipt(),
                paths.registry(),
                paths.protocol(),
                paths.manifest(),
                paths.policy(),
                paths.tools(),
                paths.application(),
                paths.census(),
                paths.cacheDirectory(),
                paths.outputs());
        return new ValidatedExperimentInputs(validated, validated.snapshots().get("census"));
    }

    private ValidatedDiscoveryInputs validate(
            String phase,
            Path receiptPath,
            Path registryPath,
            Path protocolPath,
            Path manifestPath,
            Path policyPath,
            Path toolsPath,
            Path applicationPath,
            Path censusPath,
            Path cacheDirectory,
            List<Path> outputs)
            throws IOException {
        Map<String, Path> inputPaths = new HashMap<>();
        inputPaths.put("receipt", receiptPath);
        inputPaths.put("registry", registryPath);
        inputPaths.put("protocol", protocolPath);
        inputPaths.put("manifest", manifestPath);
        inputPaths.put("policy", policyPath);
        inputPaths.put("tools", toolsPath);
        inputPaths.put("application", applicationPath);
        if (censusPath != null) {
            inputPaths.put("census", censusPath);
        }
        validatePathSeparation(inputPaths, cacheDirectory, outputs);

        BoundedFileSnapshot receiptSnapshot = BoundedFileSnapshot.read(receiptPath, MAXIMUM_RECEIPT_BYTES);
        ProspectiveReceipt receipt = receiptSnapshot.parse(mapper, ProspectiveReceipt.class);
        if (!phase.equals(receipt.phase())) {
            throw new IllegalArgumentException("Prospective receipt phase does not match the command");
        }

        Map<String, BoundedFileSnapshot> snapshots = new HashMap<>();
        snapshots.put("registry", BoundedFileSnapshot.read(registryPath, MAXIMUM_REGISTRY_BYTES));
        snapshots.put("protocol", BoundedFileSnapshot.read(protocolPath, MAXIMUM_PROTOCOL_BYTES));
        snapshots.put("manifest", BoundedFileSnapshot.read(manifestPath, MAXIMUM_MANIFEST_BYTES));
        snapshots.put("policy", BoundedFileSnapshot.read(policyPath, MAXIMUM_POLICY_BYTES));
        snapshots.put("tools", BoundedFileSnapshot.read(toolsPath, MAXIMUM_TOOLS_BYTES));
        snapshots.put("application", BoundedFileSnapshot.read(applicationPath, MAXIMUM_APPLICATION_BYTES));
        if (censusPath != null) {
            snapshots.put("census", BoundedFileSnapshot.read(censusPath, MAXIMUM_CENSUS_BYTES));
        }
        validateReceiptIdentities(receipt, snapshots);

        HistoricalSourceRegistry registry = snapshots.get("registry").parse(mapper, HistoricalSourceRegistry.class);
        ProspectiveProtocol protocol = snapshots.get("protocol").parse(mapper, ProspectiveProtocol.class);
        CorpusManifest manifest = snapshots.get("manifest").parse(mapper, CorpusManifest.class);
        ExecutionPolicy policy = snapshots.get("policy").parse(mapper, ExecutionPolicy.class);
        if (!receipt.profile().equals(protocol.profile()) || !receipt.profile().equals(manifest.profile())) {
            throw new IllegalArgumentException("Prospective receipt profile does not match protocol and manifest");
        }
        ValidatedCorpusManifest validatedManifest =
                ProspectiveHoldoutValidator.validate(manifest, registry, protocol, policy.limits());
        return new ValidatedDiscoveryInputs(
                receiptSnapshot, receipt, Map.copyOf(snapshots), registry, protocol, policy, validatedManifest);
    }

    private static void validateReceiptIdentities(
            ProspectiveReceipt receipt, Map<String, BoundedFileSnapshot> snapshots) {
        if (!receipt.artifacts().stream()
                .map(ProspectiveReceipt.Artifact::role)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
                .equals(snapshots.keySet())) {
            throw new IllegalArgumentException("Prospective receipt roles do not match supplied inputs");
        }
        for (ProspectiveReceipt.Artifact artifact : receipt.artifacts()) {
            BoundedFileSnapshot snapshot = snapshots.get(artifact.role());
            if (!artifact.sha256().equals(snapshot.sha256()) || artifact.sizeBytes() != snapshot.sizeBytes()) {
                throw new IllegalArgumentException(
                        "Prospective receipt identity mismatch for role: " + artifact.role());
            }
        }
    }

    private static void validatePathSeparation(
            Map<String, Path> inputPaths, Path cacheDirectory, List<Path> outputPaths) throws IOException {
        Objects.requireNonNull(cacheDirectory, "cacheDirectory");
        List<Path> outputs = List.copyOf(Objects.requireNonNull(outputPaths, "outputs"));
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Prospective command requires at least one output");
        }
        List<Map.Entry<String, Path>> inputs = new ArrayList<>(inputPaths.entrySet());
        for (int left = 0; left < inputs.size(); left++) {
            Objects.requireNonNull(inputs.get(left).getValue(), inputs.get(left).getKey());
            for (int right = left + 1; right < inputs.size(); right++) {
                if (sameExistingFile(
                        inputs.get(left).getValue(), inputs.get(right).getValue())) {
                    throw new IllegalArgumentException("Prospective input paths alias each other");
                }
            }
        }

        Path normalizedCache = normalize(cacheDirectory);
        Set<Path> normalizedOutputs = new HashSet<>();
        for (Path output : outputs) {
            Path normalizedOutput = normalize(Objects.requireNonNull(output, "output"));
            if (!normalizedOutputs.add(normalizedOutput)) {
                throw new IllegalArgumentException("Prospective output paths alias each other");
            }
            if (Files.exists(normalizedOutput)) {
                throw new IllegalArgumentException("Prospective canonical output already exists");
            }
            if (normalizedOutput.startsWith(normalizedCache) || normalizedCache.startsWith(normalizedOutput)) {
                throw new IllegalArgumentException("Prospective output and cache paths must be disjoint");
            }
            for (Path input : inputPaths.values()) {
                Path normalizedInput = normalize(input);
                if (normalizedOutput.equals(normalizedInput)
                        || normalizedOutput.startsWith(normalizedInput)
                        || normalizedInput.startsWith(normalizedOutput)) {
                    throw new IllegalArgumentException("Prospective input and output paths must be disjoint");
                }
            }
        }
        for (Path input : inputPaths.values()) {
            Path normalizedInput = normalize(input);
            if (normalizedInput.equals(normalizedCache)
                    || normalizedInput.startsWith(normalizedCache)
                    || normalizedCache.startsWith(normalizedInput)) {
                throw new IllegalArgumentException("Prospective input and cache paths must be disjoint");
            }
        }
    }

    private static boolean sameExistingFile(Path left, Path right) throws IOException {
        Path normalizedLeft = normalize(left);
        Path normalizedRight = normalize(right);
        return normalizedLeft.equals(normalizedRight) || Files.isSameFile(normalizedLeft, normalizedRight);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    public record DiscoveryPaths(
            Path receipt,
            Path registry,
            Path protocol,
            Path manifest,
            Path policy,
            Path tools,
            Path application,
            Path cacheDirectory,
            List<Path> outputs) {
        public DiscoveryPaths {
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        }
    }

    public record ExperimentPaths(
            Path receipt,
            Path registry,
            Path protocol,
            Path manifest,
            Path policy,
            Path tools,
            Path application,
            Path census,
            Path cacheDirectory,
            List<Path> outputs) {
        public ExperimentPaths {
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        }
    }

    public record ValidatedDiscoveryInputs(
            BoundedFileSnapshot receiptSnapshot,
            ProspectiveReceipt receipt,
            Map<String, BoundedFileSnapshot> snapshots,
            HistoricalSourceRegistry registry,
            ProspectiveProtocol protocol,
            ExecutionPolicy policy,
            ValidatedCorpusManifest manifest) {
        public ValidatedDiscoveryInputs {
            snapshots = Map.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
        }
    }

    public record ValidatedExperimentInputs(ValidatedDiscoveryInputs discovery, BoundedFileSnapshot censusSnapshot) {}
}
