package dev.tlamutant.experiment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusFetcher;
import dev.tlamutant.corpus.CorpusInputSnapshot;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.ValidatedCorpusManifest;
import dev.tlamutant.prospective.ProspectiveCensusReport;
import dev.tlamutant.sandbox.PodmanExecution;
import dev.tlamutant.sandbox.PodmanRunner;
import dev.tlamutant.sandbox.SandboxInput;
import dev.tlamutant.security.StrictJson;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CorpusExperimentRunner {
    private final Path applicationJar;
    private final Path toolsJar;
    private final Path cacheRoot;
    private final ExecutionPolicy policy;
    private final OracleMode oracleMode;
    private final CorpusExperimentInputIdentity inputs;
    private final CorpusFetcher fetcher = new CorpusFetcher();
    private final ObjectMapper mapper = new ObjectMapper();

    public CorpusExperimentRunner(Path applicationJar, Path toolsJar, Path cacheRoot, ExecutionPolicy policy) {
        this(applicationJar, toolsJar, cacheRoot, policy, OracleMode.STATE_INVARIANTS, null);
    }

    public CorpusExperimentRunner(
            Path applicationJar,
            Path toolsJar,
            Path cacheRoot,
            ExecutionPolicy policy,
            OracleMode oracleMode,
            CorpusExperimentInputIdentity inputs) {
        this.applicationJar = applicationJar.toAbsolutePath().normalize();
        this.toolsJar = toolsJar.toAbsolutePath().normalize();
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
        this.policy = Objects.requireNonNull(policy, "policy");
        this.oracleMode = Objects.requireNonNull(oracleMode, "oracleMode");
        this.inputs = inputs;
        if ((oracleMode == OracleMode.FULL_PROPERTY) != (inputs != null)) {
            throw new IllegalArgumentException("Full-property corpus mode requires common input identities");
        }
    }

    public CorpusExperimentReport run(CorpusManifest manifest) {
        if (manifest.entries() == null
                || manifest.entries().size() < 10
                || manifest.entries().size() > 20) {
            throw new IllegalArgumentException("The vertical-slice corpus must contain 10 to 20 entries");
        }
        Set<String> ids = new HashSet<>();
        Set<String> domains = new HashSet<>();
        for (CorpusManifest.Entry entry : manifest.entries()) {
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Corpus entry IDs must be unique");
            }
            domains.add(entry.domain());
        }
        if (domains.size() < 3) {
            throw new IllegalArgumentException("The vertical-slice corpus must cover at least three domains");
        }

        return runEntries(manifest, null, null);
    }

    public CorpusExperimentReport run(ValidatedCorpusManifest validated, ProspectiveCensusReport prospectiveCensus) {
        Objects.requireNonNull(validated, "validated");
        Objects.requireNonNull(prospectiveCensus, "prospectiveCensus");
        CorpusManifest manifest = validated.manifest();
        if (prospectiveCensus.census().entries().size() != manifest.entries().size()) {
            throw new IllegalArgumentException("Prospective census membership differs from the validated manifest");
        }
        return runEntries(manifest, validated, prospectiveCensus);
    }

    private CorpusExperimentReport runEntries(
            CorpusManifest manifest, ValidatedCorpusManifest validated, ProspectiveCensusReport prospectiveCensus) {
        List<CorpusEntryResult> results = new ArrayList<>();
        for (int index = 0; index < manifest.entries().size(); index++) {
            CorpusManifest.Entry entry = manifest.entries().get(index);
            if (prospectiveCensus != null
                    && !prospectiveCensus.census().entries().get(index).status().equals("COMPLETED")) {
                String censusStatus =
                        prospectiveCensus.census().entries().get(index).status();
                results.add(incomplete(entry, "CENSUS_UNRESOLVED", "Census status: " + censusStatus));
            } else {
                results.add(runEntry(manifest, validated, entry));
            }
        }
        return oracleMode == OracleMode.FULL_PROPERTY
                ? CorpusExperimentReport.create(manifest.repository(), manifest.commit(), oracleMode, inputs, results)
                : CorpusExperimentReport.create(manifest.repository(), manifest.commit(), results);
    }

    private CorpusEntryResult runEntry(
            CorpusManifest manifest, ValidatedCorpusManifest validated, CorpusManifest.Entry entry) {
        try {
            Path input = validated == null
                    ? fetcher.fetch(manifest, entry, policy.limits(), cacheRoot)
                    : fetcher.fetch(validated, entry, policy.limits(), cacheRoot);
            PodmanExecution execution;
            try (CorpusInputSnapshot snapshot = CorpusInputSnapshot.create(input, entry)) {
                PodmanRunner sandbox =
                        new PodmanRunner(policy.containerImage(), applicationJar, toolsJar, entry.limits());
                SandboxInput sandboxInput = oracleMode == OracleMode.FULL_PROPERTY
                        ? SandboxInput.fullProperty(inputs.manifest(), entry.derivedSourceEdits())
                        : SandboxInput.attributed(
                                entry.typeInvariants(),
                                entry.targetInvariants(),
                                entry.adoptedTargetInvariants(),
                                entry.derivedSourceEdits());
                execution = sandbox.run(
                        snapshot.root(),
                        entry.rootModule(),
                        entry.config(),
                        Duration.ofSeconds(entry.limits().wallClockSeconds()),
                        sandboxInput);
            }
            if (execution.timedOut()) {
                return incomplete(entry, "TIMEOUT", "The bounded sandbox wall clock expired");
            }
            if (execution.outputTruncated()) {
                return incomplete(entry, "OUTPUT_LIMIT", "The bounded sandbox output was truncated");
            }
            if (execution.exitCode() != 0) {
                return incomplete(entry, "SANDBOX_ERROR", execution.stderr() + "\n" + execution.stdout());
            }
            try {
                ExperimentReport report = StrictJson.parse(mapper, execution.stdout(), ExperimentReport.class);
                return new CorpusEntryResult(entry.id(), entry.domain(), "COMPLETED", null, report);
            } catch (JsonProcessingException exception) {
                return incomplete(entry, "INVALID_REPORT", "Sandbox output was not an experiment report");
            }
        } catch (IOException | RuntimeException exception) {
            return incomplete(
                    entry, "FETCH_OR_SANDBOX_ERROR", exception.getClass().getSimpleName());
        }
    }

    private static CorpusEntryResult incomplete(CorpusManifest.Entry entry, String status, String diagnostic) {
        String safe = diagnostic == null
                ? ""
                : diagnostic
                        .replaceAll("\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])", "")
                        .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\u202A-\\u202E\\u2066-\\u2069]", " ");
        safe = safe.substring(0, Math.min(safe.length(), 2000));
        return new CorpusEntryResult(entry.id(), entry.domain(), status, safe, null);
    }
}
