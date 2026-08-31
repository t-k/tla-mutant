package dev.tlamutant.tlc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.security.BoundedFileSnapshot;
import dev.tlamutant.security.SafeRelativePath;
import dev.tlamutant.state.CanonicalState;
import dev.tlamutant.state.CanonicalValue;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TlcRunner {
    private static final int OUTPUT_LIMIT = 1024 * 1024;
    private static final LifecycleTimeouts DEFAULT_LIFECYCLE_TIMEOUTS = new LifecycleTimeouts(
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5));
    private static final Set<String> REMOVED_ENVIRONMENT = Set.of(
            "CLASSPATH",
            "TLA_LIBRARY",
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "HTTP_PROXY",
            "HTTPS_PROXY",
            "ALL_PROXY",
            "NO_PROXY",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "GOOGLE_APPLICATION_CREDENTIALS");

    private final Path javaExecutable;
    private final Path applicationJar;
    private final Path toolsJar;
    private final long maximumStates;
    private final long maximumStateBytes;
    private final RunDirectoryCleaner runDirectoryCleaner;
    private final LifecycleTimeouts lifecycleTimeouts;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TlcToolEventParser eventParser = new TlcToolEventParser();
    private final TlcOutcomeClassifier outcomeClassifier = new TlcOutcomeClassifier();

    public TlcRunner(Path javaExecutable, Path applicationJar, Path toolsJar) {
        this(javaExecutable, applicationJar, toolsJar, 1_000_000, 67_108_864);
    }

    public TlcRunner(
            Path javaExecutable, Path applicationJar, Path toolsJar, long maximumStates, long maximumStateBytes) {
        this(javaExecutable, applicationJar, toolsJar, maximumStates, maximumStateBytes, TlcRunner::deleteRunDirectory);
    }

    TlcRunner(
            Path javaExecutable,
            Path applicationJar,
            Path toolsJar,
            long maximumStates,
            long maximumStateBytes,
            RunDirectoryCleaner runDirectoryCleaner) {
        this(
                javaExecutable,
                applicationJar,
                toolsJar,
                maximumStates,
                maximumStateBytes,
                runDirectoryCleaner,
                DEFAULT_LIFECYCLE_TIMEOUTS);
    }

    TlcRunner(
            Path javaExecutable,
            Path applicationJar,
            Path toolsJar,
            long maximumStates,
            long maximumStateBytes,
            RunDirectoryCleaner runDirectoryCleaner,
            LifecycleTimeouts lifecycleTimeouts) {
        this.javaExecutable = requireRegularAbsolute(javaExecutable, "java executable");
        this.applicationJar = requireRegularAbsolute(applicationJar, "application jar");
        this.toolsJar = requireRegularAbsolute(toolsJar, "TLA+ Tools jar");
        if (maximumStates <= 0 || maximumStateBytes <= 0) {
            throw new IllegalArgumentException("TLC state limits must be positive");
        }
        this.maximumStates = maximumStates;
        this.maximumStateBytes = maximumStateBytes;
        this.runDirectoryCleaner = java.util.Objects.requireNonNull(runDirectoryCleaner, "runDirectoryCleaner");
        this.lifecycleTimeouts = java.util.Objects.requireNonNull(lifecycleTimeouts, "lifecycleTimeouts");
    }

    public static TlcRunner forCurrentJava(Path applicationJar, Path toolsJar) {
        return new TlcRunner(
                Path.of(System.getProperty("java.home"), "bin", "java"),
                applicationJar.toAbsolutePath(),
                toolsJar.toAbsolutePath());
    }

    public static TlcRunner forCurrentJava(
            Path applicationJar, Path toolsJar, long maximumStates, long maximumStateBytes) {
        return new TlcRunner(
                Path.of(System.getProperty("java.home"), "bin", "java"),
                applicationJar.toAbsolutePath(),
                toolsJar.toAbsolutePath(),
                maximumStates,
                maximumStateBytes);
    }

    public TlcExecution run(
            Path modelDirectory, String moduleFile, String configFile, Path stateOutput, Duration timeout) {
        return run(modelDirectory, moduleFile, configFile, stateOutput, timeout, TlcOracleProfile.LEGACY);
    }

    public TlcExecution runFullProperty(
            Path modelDirectory, String moduleFile, String configFile, Path stateOutput, Duration timeout) {
        return run(modelDirectory, moduleFile, configFile, stateOutput, timeout, TlcOracleProfile.FULL_PROPERTY);
    }

    private TlcExecution run(
            Path modelDirectory,
            String moduleFile,
            String configFile,
            Path stateOutput,
            Duration timeout,
            TlcOracleProfile profile) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("TLC timeout must be positive");
        }
        Path modelRoot = modelDirectory.toAbsolutePath().normalize();
        String moduleArgument = moduleFile;
        String configArgument = configFile;
        if (profile == TlcOracleProfile.FULL_PROPERTY) {
            try {
                moduleArgument = SafeRelativePath.parse(moduleFile)
                        .resolveRegularFile(modelRoot)
                        .toString();
                configArgument = SafeRelativePath.parse(configFile)
                        .resolveRegularFile(modelRoot)
                        .toString();
            } catch (IOException exception) {
                throw new IllegalArgumentException(
                        "Full-property module or config cannot be resolved safely", exception);
            }
        }
        Path output = stateOutput.toAbsolutePath().normalize();
        Path runDirectory;
        try {
            Files.createDirectories(output.getParent());
            runDirectory = Files.createTempDirectory(output.getParent(), ".tlc-run-");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create TLC run directory", exception);
        }
        Path metadata = runDirectory.resolve("metadata");
        Path temporary = runDirectory.resolve("tmp");
        try {
            Files.createDirectory(temporary);
        } catch (IOException exception) {
            cleanupAfterFailure(runDirectory, exception);
            throw new IllegalStateException("Cannot create TLC temporary directory", exception);
        }
        List<String> command = List.of(
                javaExecutable.toString(),
                "-XX:+UseParallelGC",
                "-Djava.io.tmpdir=" + temporary,
                "-Dtla.mutant.state.path=" + output,
                "-Dtla.mutant.state.maxStates=" + maximumStates,
                "-Dtla.mutant.state.maxBytes=" + maximumStateBytes,
                "-cp",
                applicationJar + System.getProperty("path.separator") + toolsJar,
                "tlc2.TLC",
                "-tool",
                "-workers",
                "1",
                "-fp",
                "81",
                "-metadir",
                metadata.toString(),
                "-config",
                configArgument,
                "-dump",
                "class,dev.tlamutant.state.CanonicalStateWriter",
                moduleArgument);
        Path processDirectory = profile == TlcOracleProfile.LEGACY ? modelRoot : runDirectory;
        ProcessBuilder builder = new ProcessBuilder(command).directory(processDirectory.toFile());
        REMOVED_ENVIRONMENT.forEach(builder.environment()::remove);

        ProcessCapture capture = execute(builder, timeout);
        TlcExecution execution =
                capture.failure() == null ? classify(profile, output, capture) : lifecycleFailure(capture);
        try {
            runDirectoryCleaner.delete(runDirectory);
        } catch (IOException | RuntimeException cleanupFailure) {
            return cleanupFailure(execution, cleanupFailure);
        }
        return execution;
    }

    private ProcessCapture execute(ProcessBuilder builder, Duration timeout) {
        Process process = null;
        ExecutorService executor = null;
        Future<TlcOutputCollector.Capture> stdout = null;
        Future<TlcOutputCollector.Capture> stderr = null;
        TlcOutputCollector.Capture out = TlcOutputCollector.Capture.empty();
        TlcOutputCollector.Capture err = TlcOutputCollector.Capture.empty();
        boolean completed = false;
        boolean terminationAttempted = false;
        boolean restoreInterrupt = false;
        String failure = null;
        int exitCode = -1;
        try {
            process = builder.start();
            process.getOutputStream().close();
            executor = Executors.newVirtualThreadPerTaskExecutor();
            stdout = executor.submit(new TlcOutputCollector(process.getInputStream(), OUTPUT_LIMIT));
            stderr = executor.submit(new TlcOutputCollector(process.getErrorStream(), OUTPUT_LIMIT));
            try {
                completed = process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                restoreInterrupt = true;
                failure = appendFailure(failure, "TLC wait interrupted");
            }
            if (!completed) {
                terminationAttempted = true;
                Termination termination = terminate(process);
                restoreInterrupt |= termination.interrupted();
                if (!termination.reaped()) {
                    failure = appendFailure(failure, "TLC child remained alive after forced termination");
                }
            }
            long collectorDeadline =
                    System.nanoTime() + lifecycleTimeouts.collectorDrain().toNanos();
            CaptureResult capturedOut = capture(stdout, "stdout", collectorDeadline);
            out = capturedOut.capture();
            restoreInterrupt |= capturedOut.interrupted();
            failure = appendFailure(failure, capturedOut.failure());
            CaptureResult capturedErr = capture(stderr, "stderr", collectorDeadline);
            err = capturedErr.capture();
            restoreInterrupt |= capturedErr.interrupted();
            failure = appendFailure(failure, capturedErr.failure());
            if (completed && !process.isAlive()) {
                exitCode = process.exitValue();
            }
        } catch (IOException | RuntimeException exception) {
            failure = appendFailure(
                    failure, "TLC process failed: " + exception.getClass().getSimpleName());
        } finally {
            closeProcessStreams(process);
            cancel(stdout);
            cancel(stderr);
            if (executor != null) {
                executor.shutdownNow();
                try {
                    if (!executor.awaitTermination(
                            lifecycleTimeouts.executorTermination().toNanos(), TimeUnit.NANOSECONDS)) {
                        failure = appendFailure(failure, "TLC collector executor did not terminate");
                    }
                } catch (InterruptedException exception) {
                    restoreInterrupt = true;
                    failure = appendFailure(failure, "TLC collector executor shutdown interrupted");
                }
            }
            if (process != null && process.isAlive()) {
                if (!terminationAttempted) {
                    Termination termination = terminate(process);
                    restoreInterrupt |= termination.interrupted();
                    if (!termination.reaped()) {
                        failure = appendFailure(failure, "TLC child remained alive after bounded cleanup");
                    }
                } else {
                    process.destroyForcibly();
                    failure = appendFailure(failure, "TLC child remained alive after bounded cleanup");
                }
            }
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
        return new ProcessCapture(completed, exitCode, out, err, failure);
    }

    private Termination terminate(Process process) {
        boolean interrupted = false;
        process.destroy();
        try {
            if (process.waitFor(lifecycleTimeouts.gracefulTermination().toNanos(), TimeUnit.NANOSECONDS)) {
                return new Termination(true, false);
            }
        } catch (InterruptedException exception) {
            interrupted = true;
        }
        process.destroyForcibly();
        try {
            return new Termination(
                    process.waitFor(lifecycleTimeouts.forcedTermination().toNanos(), TimeUnit.NANOSECONDS),
                    interrupted);
        } catch (InterruptedException exception) {
            return new Termination(false, true);
        }
    }

    private CaptureResult capture(Future<TlcOutputCollector.Capture> future, String stream, long deadlineNanos) {
        if (future == null) {
            return new CaptureResult(
                    TlcOutputCollector.Capture.empty(), "TLC " + stream + " collector was not started", false);
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0 && !future.isDone()) {
            return new CaptureResult(
                    TlcOutputCollector.Capture.empty(), "TLC " + stream + " collector timed out", false);
        }
        try {
            return new CaptureResult(future.get(Math.max(0, remainingNanos), TimeUnit.NANOSECONDS), null, false);
        } catch (TimeoutException exception) {
            return new CaptureResult(
                    TlcOutputCollector.Capture.empty(), "TLC " + stream + " collector timed out", false);
        } catch (ExecutionException exception) {
            return new CaptureResult(
                    TlcOutputCollector.Capture.empty(),
                    "TLC " + stream + " collector failed: "
                            + exception.getCause().getClass().getSimpleName(),
                    false);
        } catch (InterruptedException exception) {
            return new CaptureResult(
                    TlcOutputCollector.Capture.empty(), "TLC " + stream + " collector interrupted", true);
        }
    }

    private TlcExecution classify(TlcOracleProfile profile, Path output, ProcessCapture capture) {
        TlcToolEventParser.ParseResult parsed =
                eventParser.parse(capture.stdout().text());
        List<TlcToolEvent> events = parsed.events();
        StateFile stateFile = readStateFile(output);
        TlcOutcomeClassifier.Classification classification = outcomeClassifier.classify(
                profile,
                capture.completed(),
                capture.exitCode(),
                events,
                stateFile.complete(),
                capture.outputTruncated(),
                parsed.valid()
                        && capture.stdout().validUtf8()
                        && capture.stderr().validUtf8());
        return new TlcExecution(
                classification.status(),
                classification.oracle(),
                capture.exitCode(),
                events,
                stateFile.states(),
                capture.stdout().text(),
                capture.stderr().text(),
                capture.outputTruncated(),
                classification.violatedProperty());
    }

    private static TlcExecution lifecycleFailure(ProcessCapture capture) {
        String stderr = appendDiagnostic(capture.stderr().text(), capture.failure());
        return new TlcExecution(
                ExecutionStatus.TOOL_ERROR,
                OracleOutcome.NOT_EVALUATED,
                capture.exitCode(),
                List.of(),
                List.of(),
                capture.stdout().text(),
                stderr,
                capture.outputTruncated(),
                null);
    }

    private static void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private static void closeProcessStreams(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Reap, collector, executor, and run-directory checks determine lifecycle failure.
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // Reap, collector, executor, and run-directory checks determine lifecycle failure.
        }
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {
            // Reap, collector, executor, and run-directory checks determine lifecycle failure.
        }
    }

    private static String appendFailure(String current, String addition) {
        if (addition == null || addition.isBlank()) {
            return current;
        }
        return current == null ? addition : current + "; " + addition;
    }

    private static String appendDiagnostic(String stderr, String diagnostic) {
        String separator = stderr.isEmpty() ? "" : "\n";
        String combined = stderr + separator + diagnostic;
        return combined.length() <= OUTPUT_LIMIT ? combined : combined.substring(combined.length() - OUTPUT_LIMIT);
    }

    private StateFile readStateFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return new StateFile(List.of(), false);
        }
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(
                        BoundedFileSnapshot.read(path, maximumStateBytes).bytes()),
                StandardCharsets.UTF_8))) {
            List<CanonicalState> states = new ArrayList<>();
            boolean complete = false;
            int declaredCount = -1;
            String line;
            while ((line = lines.readLine()) != null) {
                JsonNode node = mapper.readTree(line);
                String type = node.required("type").textValue();
                if (type.equals("state")) {
                    if (complete || states.size() >= maximumStates) {
                        return new StateFile(List.of(), false);
                    }
                    TreeMap<String, CanonicalValue> variables = mapper.convertValue(
                            node.required("variables"), new TypeReference<TreeMap<String, CanonicalValue>>() {});
                    String identity = stateIdentity(variables);
                    CanonicalState state = CanonicalState.of(identity);
                    if (!state.sha256().equals(node.required("sha256").textValue())) {
                        return new StateFile(List.of(), false);
                    }
                    states.add(state);
                } else if (type.equals("complete")) {
                    if (complete) {
                        return new StateFile(List.of(), false);
                    }
                    complete = true;
                    declaredCount = node.required("states").intValue();
                } else {
                    return new StateFile(List.of(), false);
                }
            }
            return new StateFile(List.copyOf(states), complete && declaredCount == states.size());
        } catch (IOException | RuntimeException exception) {
            return new StateFile(List.of(), false);
        }
    }

    private static String stateIdentity(TreeMap<String, CanonicalValue> variables) {
        StringBuilder identity = new StringBuilder();
        variables.forEach((name, value) -> identity.append(name.length())
                .append(':')
                .append(name)
                .append(':')
                .append(value.identityText())
                .append(';'));
        return identity.toString();
    }

    private static Path requireRegularAbsolute(Path path, String name) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!path.isAbsolute() || !Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(name + " must be an absolute regular file");
        }
        return normalized;
    }

    static void deleteRunDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("TLC run directory remained after cleanup");
        }
    }

    private void cleanupAfterFailure(Path runDirectory, Exception primary) {
        try {
            runDirectoryCleaner.delete(runDirectory);
        } catch (IOException | RuntimeException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private static TlcExecution cleanupFailure(TlcExecution execution, Exception failure) {
        String suffix = "TLC run cleanup failed: " + failure.getClass().getSimpleName();
        String separator = execution.stderr().isEmpty() ? "" : "\n";
        String stderr = execution.stderr() + separator + suffix;
        if (stderr.length() > OUTPUT_LIMIT) {
            stderr = stderr.substring(stderr.length() - OUTPUT_LIMIT);
        }
        return new TlcExecution(
                ExecutionStatus.TOOL_ERROR,
                OracleOutcome.NOT_EVALUATED,
                execution.exitCode(),
                execution.events(),
                execution.states(),
                execution.stdout(),
                stderr,
                execution.outputTruncated(),
                null);
    }

    @FunctionalInterface
    interface RunDirectoryCleaner {
        void delete(Path path) throws IOException;
    }

    record LifecycleTimeouts(
            Duration gracefulTermination,
            Duration forcedTermination,
            Duration collectorDrain,
            Duration executorTermination) {
        LifecycleTimeouts {
            requirePositive(gracefulTermination, "Graceful termination timeout");
            requirePositive(forcedTermination, "Forced termination timeout");
            requirePositive(collectorDrain, "Collector drain timeout");
            requirePositive(executorTermination, "Executor termination timeout");
        }

        private static void requirePositive(Duration duration, String label) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(label + " must be positive");
            }
        }
    }

    private record ProcessCapture(
            boolean completed,
            int exitCode,
            TlcOutputCollector.Capture stdout,
            TlcOutputCollector.Capture stderr,
            String failure) {
        private boolean outputTruncated() {
            return stdout.truncated() || stderr.truncated();
        }
    }

    private record CaptureResult(TlcOutputCollector.Capture capture, String failure, boolean interrupted) {}

    private record Termination(boolean reaped, boolean interrupted) {}

    private record StateFile(List<CanonicalState> states, boolean complete) {}
}
