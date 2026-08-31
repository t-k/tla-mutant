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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class TlcRunner {
    private static final int OUTPUT_LIMIT = 1024 * 1024;
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
        this.javaExecutable = requireRegularAbsolute(javaExecutable, "java executable");
        this.applicationJar = requireRegularAbsolute(applicationJar, "application jar");
        this.toolsJar = requireRegularAbsolute(toolsJar, "TLA+ Tools jar");
        if (maximumStates <= 0 || maximumStateBytes <= 0) {
            throw new IllegalArgumentException("TLC state limits must be positive");
        }
        this.maximumStates = maximumStates;
        this.maximumStateBytes = maximumStateBytes;
        this.runDirectoryCleaner = java.util.Objects.requireNonNull(runDirectoryCleaner, "runDirectoryCleaner");
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

        TlcExecution execution;
        try {
            Process process = builder.start();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<TlcOutputCollector.Capture> stdout =
                        executor.submit(new TlcOutputCollector(process.getInputStream(), OUTPUT_LIMIT));
                Future<TlcOutputCollector.Capture> stderr =
                        executor.submit(new TlcOutputCollector(process.getErrorStream(), OUTPUT_LIMIT));
                boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!completed) {
                    process.destroy();
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(5, TimeUnit.SECONDS);
                    }
                }
                TlcOutputCollector.Capture out = stdout.get();
                TlcOutputCollector.Capture err = stderr.get();
                int exitCode = completed ? process.exitValue() : -1;
                TlcToolEventParser.ParseResult parsed = eventParser.parse(out.text());
                List<TlcToolEvent> events = parsed.events();
                StateFile stateFile = readStateFile(output);
                TlcOutcomeClassifier.Classification classification = outcomeClassifier.classify(
                        profile,
                        completed,
                        exitCode,
                        events,
                        stateFile.complete(),
                        out.truncated() || err.truncated(),
                        parsed.valid() && out.validUtf8() && err.validUtf8());
                execution = new TlcExecution(
                        classification.status(),
                        classification.oracle(),
                        exitCode,
                        events,
                        stateFile.states(),
                        out.text(),
                        err.text(),
                        out.truncated() || err.truncated(),
                        classification.violatedProperty());
            }
        } catch (IOException | InterruptedException | ExecutionException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            execution = new TlcExecution(
                    ExecutionStatus.TOOL_ERROR,
                    OracleOutcome.NOT_EVALUATED,
                    -1,
                    List.of(),
                    List.of(),
                    "",
                    exception.getClass().getSimpleName(),
                    false);
        }
        try {
            runDirectoryCleaner.delete(runDirectory);
        } catch (IOException | RuntimeException cleanupFailure) {
            return cleanupFailure(execution, cleanupFailure);
        }
        return execution;
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

    private record StateFile(List<CanonicalState> states, boolean complete) {}
}
