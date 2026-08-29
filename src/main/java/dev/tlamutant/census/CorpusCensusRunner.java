package dev.tlamutant.census;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusFetcher;
import dev.tlamutant.corpus.CorpusInputSnapshot;
import dev.tlamutant.corpus.CorpusManifest;
import dev.tlamutant.corpus.ValidatedCorpusManifest;
import dev.tlamutant.sandbox.PodmanExecution;
import dev.tlamutant.sandbox.PodmanRunner;
import dev.tlamutant.security.StrictJson;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CorpusCensusRunner {
    private final Path applicationJar;
    private final Path toolsJar;
    private final Path cacheRoot;
    private final ExecutionPolicy policy;
    private final CorpusFetcher fetcher = new CorpusFetcher();
    private final ObjectMapper mapper = new ObjectMapper();

    public CorpusCensusRunner(Path applicationJar, Path toolsJar, Path cacheRoot, ExecutionPolicy policy) {
        this.applicationJar = applicationJar.toAbsolutePath().normalize();
        this.toolsJar = toolsJar.toAbsolutePath().normalize();
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public CorpusCensusReport run(CorpusManifest manifest, CensusInputIdentity inputs) {
        return runEntries(manifest, null, inputs);
    }

    public CorpusCensusReport run(ValidatedCorpusManifest validated, CensusInputIdentity inputs) {
        Objects.requireNonNull(validated, "validated");
        return runEntries(validated.manifest(), validated, inputs);
    }

    private CorpusCensusReport runEntries(
            CorpusManifest manifest, ValidatedCorpusManifest validated, CensusInputIdentity inputs) {
        List<CorpusCensusEntryResult> results = new ArrayList<>();
        for (CorpusManifest.Entry entry : manifest.entries()) {
            results.add(runEntry(manifest, validated, entry));
        }
        return CorpusCensusReport.create(manifest.profile(), manifest.repository(), manifest.commit(), inputs, results);
    }

    private CorpusCensusEntryResult runEntry(
            CorpusManifest manifest, ValidatedCorpusManifest validated, CorpusManifest.Entry entry) {
        try {
            Path input = validated == null
                    ? fetcher.fetch(manifest, entry, policy.limits(), cacheRoot)
                    : fetcher.fetch(validated, entry, policy.limits(), cacheRoot);
            PodmanExecution execution;
            try (CorpusInputSnapshot snapshot = CorpusInputSnapshot.create(input, entry)) {
                PodmanRunner sandbox =
                        new PodmanRunner(policy.containerImage(), applicationJar, toolsJar, entry.limits());
                execution = sandbox.runCensus(
                        snapshot.root(),
                        entry.rootModule(),
                        entry.config(),
                        Duration.ofSeconds(entry.limits().wallClockSeconds()),
                        entry.derivedSourceEdits());
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
                GcdCensusReport report = StrictJson.parse(mapper, execution.stdout(), GcdCensusReport.class);
                return completed(entry, report);
            } catch (JsonProcessingException exception) {
                return incomplete(entry, "INVALID_REPORT", "Sandbox output was not a GCD census report");
            }
        } catch (IOException | RuntimeException exception) {
            return incomplete(
                    entry, "FETCH_OR_SANDBOX_ERROR", exception.getClass().getSimpleName());
        }
    }

    private static CorpusCensusEntryResult completed(CorpusManifest.Entry entry, GcdCensusReport report) {
        return new CorpusCensusEntryResult(entry.id(), entry.domain(), entry.selection(), "COMPLETED", null, report);
    }

    private static CorpusCensusEntryResult incomplete(CorpusManifest.Entry entry, String status, String diagnostic) {
        String safe = diagnostic == null
                ? ""
                : diagnostic
                        .replaceAll("\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])", "")
                        .replaceAll(
                                "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]",
                                " ");
        safe = safe.substring(0, Math.min(safe.length(), 2000));
        return new CorpusCensusEntryResult(entry.id(), entry.domain(), entry.selection(), status, safe, null);
    }
}
