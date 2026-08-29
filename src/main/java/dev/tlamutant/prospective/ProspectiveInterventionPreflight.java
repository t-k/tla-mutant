package dev.tlamutant.prospective;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.census.CensusInputIdentity;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.ProspectiveInterventionValidator;
import dev.tlamutant.corpus.ValidatedCorpusManifest;
import dev.tlamutant.security.BoundedFileSnapshot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProspectiveInterventionPreflight {
    private static final long MAXIMUM_RECEIPT_BYTES = 1024L * 1024;
    private static final long MAXIMUM_PROTOCOL_BYTES = 2L * 1024 * 1024;
    private static final long MAXIMUM_MANIFEST_BYTES = 8L * 1024 * 1024;
    private static final long MAXIMUM_POLICY_BYTES = 1024L * 1024;
    private static final long MAXIMUM_BINARY_BYTES = 128L * 1024 * 1024;
    private static final long MAXIMUM_REPORT_BYTES = 256L * 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper();

    public ValidatedInputs validate(Paths paths) throws IOException {
        Objects.requireNonNull(paths, "paths");
        BoundedFileSnapshot receiptSnapshot = BoundedFileSnapshot.read(paths.receipt(), MAXIMUM_RECEIPT_BYTES);
        ProspectiveReceipt receipt = receiptSnapshot.parse(mapper, ProspectiveReceipt.class);
        boolean censusPhase = receipt.phase().equals("intervention-census");
        boolean experimentPhase = receipt.phase().equals("intervention-experiment");
        if ((!censusPhase && !experimentPhase) || !receipt.profile().equals("gcd-prospective-v11")) {
            throw new IllegalArgumentException("Prospective intervention receipt phase or profile is invalid");
        }
        Set<String> expectedRoles = censusPhase
                ? ProspectiveReceipt.INTERVENTION_CENSUS_ROLES
                : ProspectiveReceipt.INTERVENTION_EXPERIMENT_ROLES;
        if (!paths.inputs().keySet().equals(expectedRoles)) {
            throw new IllegalArgumentException("Prospective intervention input roles differ from the receipt phase");
        }
        Map<String, Path> separatedInputs = new HashMap<>(paths.inputs());
        separatedInputs.put("receipt", paths.receipt());
        ProspectivePreflight.validatePathSeparation(separatedInputs, paths.cacheDirectory(), paths.outputs());

        Map<String, BoundedFileSnapshot> snapshots = new HashMap<>();
        for (Map.Entry<String, Path> input : paths.inputs().entrySet()) {
            snapshots.put(input.getKey(), BoundedFileSnapshot.read(input.getValue(), maximumBytes(input.getKey())));
        }
        ProspectivePreflight.validateReceiptIdentities(receipt, snapshots);

        ProspectiveReceipt v10DiscoveryReceipt = parse("v10-discovery-receipt", snapshots, ProspectiveReceipt.class);
        ProspectiveReceipt v10ExperimentReceipt = parse("v10-experiment-receipt", snapshots, ProspectiveReceipt.class);
        ProspectiveProtocol v10Protocol = parse("v10-protocol", snapshots, ProspectiveProtocol.class);
        CorpusManifest v10Manifest = parse("v10-manifest", snapshots, CorpusManifest.class);
        ProspectiveCensusReport v10Census = parse("v10-census", snapshots, ProspectiveCensusReport.class);
        ProspectiveDiscoveryReport v10Discovery = parse("v10-discovery", snapshots, ProspectiveDiscoveryReport.class);
        ProspectiveReviewDossier dossier = parse("dossier", snapshots, ProspectiveReviewDossier.class);
        ProspectiveReviewLedger ledger = parse("review", snapshots, ProspectiveReviewLedger.class);
        ProspectiveProtocol v11Protocol = parse("v11-protocol", snapshots, ProspectiveProtocol.class);
        CorpusManifest v11Manifest = parse("v11-manifest", snapshots, CorpusManifest.class);
        ExecutionPolicy policy = parse("policy", snapshots, ExecutionPolicy.class);

        validateV10Evidence(
                snapshots,
                v10DiscoveryReceipt,
                v10ExperimentReceipt,
                v10Protocol,
                v10Manifest,
                v10Census,
                v10Discovery,
                dossier);
        requireProtocolContinuation(v10Protocol, v11Protocol);
        ProspectiveReviewValidator.ValidatedReview review = ProspectiveReviewValidator.validate(
                dossier,
                artifact(snapshots.get("dossier")),
                ledger,
                v10Protocol,
                artifact(snapshots.get("v10-protocol")));
        ValidatedCorpusManifest manifest = ProspectiveInterventionValidator.validateManifest(
                v10Manifest, v11Manifest, dossier, ledger, review, policy.limits());

        ProspectiveCensusReport v11Census = null;
        BoundedFileSnapshot censusReceiptSnapshot = null;
        if (experimentPhase) {
            censusReceiptSnapshot = snapshots.get("census-receipt");
            ProspectiveReceipt censusReceipt = censusReceiptSnapshot.parse(mapper, ProspectiveReceipt.class);
            if (!censusReceipt.phase().equals("intervention-census")
                    || !censusReceipt.profile().equals(receipt.profile())) {
                throw new IllegalArgumentException("Prospective intervention census receipt is invalid");
            }
            Map<String, BoundedFileSnapshot> censusSnapshots = new HashMap<>();
            for (String role : ProspectiveReceipt.INTERVENTION_CENSUS_ROLES) {
                censusSnapshots.put(role, snapshots.get(role));
            }
            ProspectivePreflight.validateReceiptIdentities(censusReceipt, censusSnapshots);
            v11Census = parse("v11-census", snapshots, ProspectiveCensusReport.class);
            CensusInputIdentity inputs = inputs(snapshots);
            ProspectiveCensusValidator.validate(v11Manifest, v11Census, artifact(censusReceiptSnapshot), inputs);
            manifest = ProspectiveInterventionValidator.validate(
                    v10Manifest, v11Manifest, dossier, ledger, review, v11Census, policy.limits());
        }
        return new ValidatedInputs(
                receiptSnapshot,
                receipt,
                Map.copyOf(snapshots),
                v10Manifest,
                v11Protocol,
                policy,
                manifest,
                dossier,
                ledger,
                review,
                censusReceiptSnapshot,
                v11Census);
    }

    private void validateV10Evidence(
            Map<String, BoundedFileSnapshot> snapshots,
            ProspectiveReceipt discoveryReceipt,
            ProspectiveReceipt experimentReceipt,
            ProspectiveProtocol protocol,
            CorpusManifest manifest,
            ProspectiveCensusReport census,
            ProspectiveDiscoveryReport discovery,
            ProspectiveReviewDossier dossier) {
        if (!discoveryReceipt.phase().equals("discovery")
                || !experimentReceipt.phase().equals("experiment")
                || !discoveryReceipt.profile().equals("gcd-prospective-v10")
                || !experimentReceipt.profile().equals("gcd-prospective-v10")
                || !protocol.profile().equals("gcd-prospective-v10")
                || !manifest.profile().equals("gcd-prospective-v10")) {
            throw new IllegalArgumentException("Frozen v10 phase or profile identity is invalid");
        }
        requireReceiptArtifact(discoveryReceipt, "protocol", snapshots.get("v10-protocol"));
        requireReceiptArtifact(discoveryReceipt, "manifest", snapshots.get("v10-manifest"));
        requireReceiptArtifact(experimentReceipt, "protocol", snapshots.get("v10-protocol"));
        requireReceiptArtifact(experimentReceipt, "manifest", snapshots.get("v10-manifest"));
        requireReceiptArtifact(experimentReceipt, "census", snapshots.get("v10-census"));
        requireReceiptArtifact(experimentReceipt, "discovery-receipt", snapshots.get("v10-discovery-receipt"));
        for (String role : ProspectiveReceipt.DISCOVERY_ROLES) {
            ProspectiveReceipt.Artifact left = discoveryReceipt.artifact(role);
            ProspectiveReceipt.Artifact right = experimentReceipt.artifact(role);
            if (!left.sha256().equals(right.sha256()) || left.sizeBytes() != right.sizeBytes()) {
                throw new IllegalArgumentException("Frozen v10 discovery and experiment receipts diverge");
            }
        }
        if (!discovery.discoveryReceipt().equals(artifact(snapshots.get("v10-discovery-receipt")))
                || !discovery.experimentReceipt().equals(artifact(snapshots.get("v10-experiment-receipt")))
                || !discovery.censusArtifact().equals(artifact(snapshots.get("v10-census")))
                || !discovery.census().equals(census)
                || !census.receipt().equals(artifact(snapshots.get("v10-discovery-receipt")))) {
            throw new IllegalArgumentException("Frozen v10 report chain is inconsistent");
        }
        ProspectiveReviewDossier derived = ProspectiveReviewDossier.derive(
                manifest,
                discovery,
                artifact(snapshots.get("v10-protocol")),
                artifact(snapshots.get("v10-manifest")),
                artifact(snapshots.get("v10-discovery")));
        if (!dossier.equals(derived)) {
            throw new IllegalArgumentException("Frozen v10 dossier does not derive from the discovery");
        }
    }

    private static void requireProtocolContinuation(ProspectiveProtocol v10, ProspectiveProtocol v11) {
        if (!v11.profile().equals("gcd-prospective-v11")
                || v10.schemaVersion() != v11.schemaVersion()
                || !v10.repository().equals(v11.repository())
                || !v10.commit().equals(v11.commit())
                || v10.standardRiskEntries() != v11.standardRiskEntries()
                || v10.highRiskEntries() != v11.highRiskEntries()
                || v10.interventionCap() != v11.interventionCap()
                || !v10.historicalSet().equals(v11.historicalSet())
                || !v10.eligibility().equals(v11.eligibility())
                || !v10.rubric().equals(v11.rubric())
                || !v10.entries().equals(v11.entries())) {
            throw new IllegalArgumentException("Prospective v11 protocol changed more than its profile");
        }
    }

    private static void requireReceiptArtifact(ProspectiveReceipt receipt, String role, BoundedFileSnapshot snapshot) {
        ProspectiveReceipt.Artifact artifact = receipt.artifact(role);
        if (!artifact.sha256().equals(snapshot.sha256()) || artifact.sizeBytes() != snapshot.sizeBytes()) {
            throw new IllegalArgumentException("Frozen v10 receipt identity mismatch for role: " + role);
        }
    }

    private <T> T parse(String role, Map<String, BoundedFileSnapshot> snapshots, Class<T> type) throws IOException {
        return snapshots.get(role).parse(mapper, type);
    }

    private static long maximumBytes(String role) {
        if (role.endsWith("receipt")) {
            return MAXIMUM_RECEIPT_BYTES;
        }
        if (role.endsWith("protocol")) {
            return MAXIMUM_PROTOCOL_BYTES;
        }
        if (role.endsWith("manifest")) {
            return MAXIMUM_MANIFEST_BYTES;
        }
        if (role.equals("policy")) {
            return MAXIMUM_POLICY_BYTES;
        }
        if (role.equals("tools") || role.equals("application")) {
            return MAXIMUM_BINARY_BYTES;
        }
        return MAXIMUM_REPORT_BYTES;
    }

    public static CensusInputIdentity inputs(Map<String, BoundedFileSnapshot> snapshots) {
        return new CensusInputIdentity(
                artifact(snapshots.get("v11-manifest")),
                artifact(snapshots.get("policy")),
                artifact(snapshots.get("tools")),
                artifact(snapshots.get("application")));
    }

    public static CensusInputIdentity.Artifact artifact(BoundedFileSnapshot snapshot) {
        return new CensusInputIdentity.Artifact(snapshot.sha256(), snapshot.sizeBytes());
    }

    public record Paths(Path receipt, Map<String, Path> inputs, Path cacheDirectory, List<Path> outputs) {
        public Paths {
            inputs = Map.copyOf(Objects.requireNonNull(inputs, "inputs"));
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        }
    }

    public record ValidatedInputs(
            BoundedFileSnapshot receiptSnapshot,
            ProspectiveReceipt receipt,
            Map<String, BoundedFileSnapshot> snapshots,
            CorpusManifest v10Manifest,
            ProspectiveProtocol protocol,
            ExecutionPolicy policy,
            ValidatedCorpusManifest manifest,
            ProspectiveReviewDossier dossier,
            ProspectiveReviewLedger ledger,
            ProspectiveReviewValidator.ValidatedReview review,
            BoundedFileSnapshot censusReceiptSnapshot,
            ProspectiveCensusReport census) {
        public ValidatedInputs {
            snapshots = Map.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
        }
    }
}
