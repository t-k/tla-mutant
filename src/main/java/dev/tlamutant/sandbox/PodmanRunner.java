package dev.tlamutant.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PodmanRunner {
    private static final String SECCOMP_SHA256 = "886ae167646b7e5db381ecf7c31e6de720a8e8da15cf3202fe1f67f424af2b75";
    private static final String RUN_LABEL = "dev.tlamutant.run";
    private static final Duration PODMAN_COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private final String image;
    private final Path applicationJar;
    private final Path toolsJar;
    private final ExecutionPolicy.Limits limits;
    private final ObjectMapper mapper = new ObjectMapper();

    public PodmanRunner(String image, Path applicationJar, Path toolsJar, ExecutionPolicy.Limits limits) {
        if (image == null || !image.matches("[A-Za-z0-9./_-]+@sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Container image must be an exact SHA-256 reference");
        }
        this.image = image;
        this.applicationJar = requireRegularFile(applicationJar, "application jar");
        this.toolsJar = requireRegularFile(toolsJar, "tools jar");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public PodmanExecution run(Path inputDirectory, String module, String config, Duration timeout) {
        return run(inputDirectory, module, config, timeout, SandboxTask.EXPERIMENT, List.of());
    }

    public PodmanExecution run(
            Path inputDirectory, String module, String config, Duration timeout, List<String> stateInvariants) {
        return run(inputDirectory, module, config, timeout, SandboxTask.EXPERIMENT, stateInvariants);
    }

    public PodmanExecution runCensus(Path inputDirectory, String module, String config, Duration timeout) {
        return run(inputDirectory, module, config, timeout, SandboxTask.CENSUS, List.of());
    }

    private PodmanExecution run(
            Path inputDirectory,
            String module,
            String config,
            Duration timeout,
            SandboxTask task,
            List<String> stateInvariants) {
        Path input = inputDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(input) || Files.isSymbolicLink(input)) {
            throw new IllegalArgumentException("Input must be a real directory");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Sandbox timeout must be positive");
        }
        Duration effectiveTimeout = timeout;
        Duration policyTimeout = Duration.ofSeconds(Math.min(limits.cpuSeconds(), limits.wallClockSeconds()));
        if (effectiveTimeout.compareTo(policyTimeout) > 0) {
            effectiveTimeout = policyTimeout;
        }
        EngineIdentity identity = preflight();
        String runToken = randomHex(16);
        String name = "tla-mutant-" + runToken;
        String scratchVolume = "tla-mutant-scratch-" + runToken;
        String containerId = "";
        boolean volumeCreateAttempted = false;
        boolean containerCreateAttempted = false;
        try {
            volumeCreateAttempted = true;
            createScratchVolume(scratchVolume, runToken);
            verifyScratchVolume(scratchVolume, runToken);
            List<String> create = PodmanCommand.create(
                    name,
                    runToken,
                    scratchVolume,
                    image,
                    input,
                    applicationJar,
                    toolsJar,
                    module,
                    config,
                    identity.uid(),
                    identity.gid(),
                    limits,
                    task,
                    stateInvariants);
            containerCreateAttempted = true;
            String createOutput = runChecked(create).trim();
            if (!createOutput.matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("Podman returned an invalid container identity");
            }
            containerId = createOutput;
            verifyContainer(containerId, name, runToken);
            return startAttached(containerId, effectiveTimeout);
        } finally {
            boolean restoreInterrupt = Thread.interrupted();
            try {
                try {
                    if (containerCreateAttempted) {
                        cleanupContainer(containerId, name, runToken);
                    }
                } finally {
                    restoreInterrupt |= Thread.interrupted();
                    if (volumeCreateAttempted) {
                        cleanupVolume(scratchVolume, runToken);
                    }
                }
            } finally {
                restoreInterrupt |= Thread.interrupted();
                if (restoreInterrupt) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void createScratchVolume(String name, String runToken) {
        String options = scratchOptions();
        String created = runChecked(List.of(
                        "podman",
                        "volume",
                        "create",
                        "--label",
                        RUN_LABEL + "=" + runToken,
                        "--opt",
                        "type=tmpfs",
                        "--opt",
                        "device=tmpfs",
                        "--opt",
                        "nocopy",
                        "--opt",
                        "o=" + options,
                        name))
                .trim();
        if (!created.equals(name)) {
            throw new IllegalStateException("Podman returned an unexpected scratch-volume name");
        }
    }

    private String scratchOptions() {
        return "size="
                + limits.fileBytes()
                + ",nr_inodes="
                + limits.fileCount()
                + ",uid="
                + 0
                + ",gid="
                + 0
                + ",mode=700,nodev,nosuid,noexec";
    }

    private void verifyScratchVolume(String name, String runToken) {
        try {
            JsonNode inspect = mapper.readTree(runChecked(List.of("podman", "volume", "inspect", name)))
                    .get(0);
            if (!inspect.required("Name").textValue().equals(name)
                    || !inspect.at("/Labels/" + RUN_LABEL).asText().equals(runToken)
                    || !inspect.at("/Options/type").asText().equals("tmpfs")
                    || !inspect.at("/Options").has("nocopy")
                    || !inspect.at("/Options/o").asText().equals(scratchOptions())) {
                throw new IllegalStateException("Scratch tmpfs limits were not preserved");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect scratch tmpfs volume", exception);
        }
    }

    private EngineIdentity preflight() {
        try {
            JsonNode info = mapper.readTree(runChecked(List.of("podman", "info", "--format", "json")));
            if (!info.at("/host/security/rootless").asBoolean()
                    || !info.at("/host/cgroupVersion").asText().equals("v2")) {
                throw new IllegalStateException("Podman must be rootless with cgroup v2");
            }
            JsonNode imageInfo = mapper.readTree(runChecked(List.of("podman", "image", "inspect", image)));
            String expectedDigest = image.substring(image.indexOf("sha256:"));
            if (!imageInfo.get(0).required("Digest").textValue().equals(expectedDigest)) {
                throw new IllegalStateException("Pinned image digest is not loaded exactly");
            }
            String seccomp = runChecked(List.of(
                    "podman",
                    "machine",
                    "ssh",
                    "podman-machine-default",
                    "sha256sum",
                    "/usr/share/containers/seccomp.json"));
            if (!seccomp.startsWith(SECCOMP_SHA256 + " ")) {
                throw new IllegalStateException("Seccomp profile digest mismatch");
            }
            return new EngineIdentity(mappingHostIdentity(info, "uidmap"), mappingHostIdentity(info, "gidmap"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot validate Podman preflight", exception);
        }
    }

    private static int mappingHostIdentity(JsonNode info, String mappingName) {
        JsonNode mappings = info.at("/host/idMappings/" + mappingName);
        if (!mappings.isArray()) {
            throw new IllegalStateException("Podman ID mapping is missing");
        }
        for (JsonNode mapping : mappings) {
            long containerId = mapping.required("container_id").longValue();
            long hostId = mapping.required("host_id").longValue();
            long size = mapping.required("size").longValue();
            if (containerId == 0 && size == 1 && hostId > 0 && hostId <= Integer.MAX_VALUE) {
                return (int) hostId;
            }
        }
        throw new IllegalStateException("Podman root identity mapping is unsupported");
    }

    private void verifyContainer(String containerId, String expectedName, String runToken) {
        try {
            JsonNode inspect = mapper.readTree(runChecked(List.of("podman", "inspect", containerId)))
                    .get(0);
            if (!inspect.required("Id").textValue().equals(containerId)
                    || !inspect.required("Name").textValue().equals(expectedName)
                    || !inspect.at("/Config/Labels/" + RUN_LABEL).asText().equals(runToken)) {
                throw new IllegalStateException("Created container identity mismatch");
            }
            JsonNode hostConfig = inspect.required("HostConfig");
            if (!hostConfig.required("ReadonlyRootfs").asBoolean()
                    || !hostConfig.required("NetworkMode").textValue().equals("none")
                    || hostConfig.required("Privileged").asBoolean()
                    || hostConfig.required("PidsLimit").longValue() != limits.pids()
                    || hostConfig.required("Memory").longValue() != limits.memoryBytes()
                    || hostConfig.required("MemorySwap").longValue() != limits.memoryBytes()
                    || hostConfig.required("NanoCpus").longValue() != 1_000_000_000L) {
                throw new IllegalStateException("Podman did not preserve the requested resource boundary");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect created container", exception);
        }
    }

    private PodmanExecution startAttached(String containerId, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder("podman", "start", "--attach", containerId);
        Process process = null;
        ExecutorService executor = null;
        Future<Capture> stdout = null;
        Future<Capture> stderr = null;
        try {
            process = builder.start();
            process.getOutputStream().close();
            executor = Executors.newVirtualThreadPerTaskExecutor();
            stdout = executor.submit(new Collector(process.getInputStream(), limits.stdoutBytes()));
            stderr = executor.submit(new Collector(process.getErrorStream(), limits.stderrBytes()));
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                runUnchecked(List.of("podman", "stop", "--time", "2", containerId));
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    runUnchecked(List.of("podman", "kill", containerId));
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        terminate(process);
                    }
                }
            }
            Capture out = stdout.get(5, TimeUnit.SECONDS);
            Capture err = stderr.get(5, TimeUnit.SECONDS);
            return new PodmanExecution(
                    containerId,
                    completed ? process.exitValue() : -1,
                    out.text().trim(),
                    err.text(),
                    !completed,
                    out.truncated() || err.truncated());
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Sandbox execution failed", exception);
        } finally {
            closeProcessStreams(process);
            if (stdout != null && !stdout.isDone()) {
                stdout.cancel(true);
            }
            if (stderr != null && !stderr.isDone()) {
                stderr.cancel(true);
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            terminateIfAlive(process);
        }
    }

    private void cleanupContainer(String containerId, String expectedName, String runToken) {
        CommandResult existence =
                runCommand(List.of("podman", "container", "exists", expectedName), PODMAN_COMMAND_TIMEOUT, 64 * 1024);
        if (existence.exitCode() == 1) {
            return;
        }
        requireExit(existence, 0, "Cannot establish sandbox container presence");
        try {
            JsonNode inspect = mapper.readTree(runChecked(List.of("podman", "inspect", expectedName)))
                    .get(0);
            String actualId = inspect.required("Id").textValue();
            if (!inspect.required("Name").textValue().equals(expectedName)
                    || !inspect.at("/Config/Labels/" + RUN_LABEL).asText().equals(runToken)
                    || (!containerId.isEmpty() && !actualId.equals(containerId))) {
                throw new IllegalStateException("Refusing to clean a container outside this run");
            }
            runChecked(List.of("podman", "rm", "--force", "--time", "2", actualId));
            requireAbsent("container", expectedName, "Sandbox container remained after cleanup");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect sandbox container for cleanup", exception);
        }
    }

    private void cleanupVolume(String name, String runToken) {
        CommandResult existence =
                runCommand(List.of("podman", "volume", "exists", name), PODMAN_COMMAND_TIMEOUT, 64 * 1024);
        if (existence.exitCode() == 1) {
            return;
        }
        requireExit(existence, 0, "Cannot establish scratch-volume presence");
        try {
            JsonNode inspect = mapper.readTree(runChecked(List.of("podman", "volume", "inspect", name)))
                    .get(0);
            if (!inspect.required("Name").textValue().equals(name)
                    || !inspect.at("/Labels/" + RUN_LABEL).asText().equals(runToken)) {
                throw new IllegalStateException("Refusing to clean a volume outside this run");
            }
            runChecked(List.of("podman", "volume", "rm", "--force", name));
            requireAbsent("volume", name, "Scratch volume remained after cleanup");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect scratch volume for cleanup", exception);
        }
    }

    private static void requireAbsent(String kind, String identity, String message) {
        CommandResult existence =
                runCommand(List.of("podman", kind, "exists", identity), PODMAN_COMMAND_TIMEOUT, 64 * 1024);
        requireExit(existence, 1, message);
    }

    private static void requireExit(CommandResult result, int expected, String message) {
        if (result.exitCode() != expected) {
            throw new IllegalStateException(message + ": " + result.output());
        }
    }

    private static Path requireRegularFile(Path path, String name) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
            throw new IllegalArgumentException(name + " must be a real regular file");
        }
        return normalized;
    }

    private static String runChecked(List<String> command) {
        CommandResult result = runCommand(command, PODMAN_COMMAND_TIMEOUT, 1024 * 1024);
        if (result.exitCode() != 0 || result.truncated()) {
            throw new IllegalStateException("Command failed: " + command.get(0) + ": " + result.output());
        }
        return result.output();
    }

    private static CommandResult runCommand(List<String> command, Duration timeout, long outputLimit) {
        Process process = null;
        ExecutorService executor = null;
        Future<Capture> capture = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            executor = Executors.newVirtualThreadPerTaskExecutor();
            capture = executor.submit(new Collector(process.getInputStream(), outputLimit));
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminate(process);
                throw new IllegalStateException("Command exceeded its absolute deadline: " + command.get(0));
            }
            Capture output = capture.get(5, TimeUnit.SECONDS);
            return new CommandResult(process.exitValue(), output.text(), output.truncated());
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Cannot execute " + command.get(0), exception);
        } finally {
            closeProcessStreams(process);
            if (capture != null && !capture.isDone()) {
                capture.cancel(true);
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            terminateIfAlive(process);
        }
    }

    private static void runUnchecked(List<String> command) {
        try {
            runCommand(command, Duration.ofSeconds(10), 64 * 1024);
        } catch (IllegalStateException ignored) {
            // Best effort is used only before exact labeled cleanup.
        }
    }

    private static void closeProcessStreams(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // Closing is best effort after bounded collection.
        }
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {
            // Closing is best effort after bounded collection.
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Closing is best effort after bounded collection.
        }
    }

    private static void terminateIfAlive(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        boolean restoreInterrupt = Thread.interrupted();
        try {
            terminate(process);
        } catch (InterruptedException exception) {
            restoreInterrupt = true;
            Thread.interrupted();
            process.destroyForcibly();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Exact child process could not be reaped after interruption");
                }
            } catch (InterruptedException secondInterrupt) {
                restoreInterrupt = true;
                Thread.interrupted();
                process.destroyForcibly();
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Exact child process could not be reaped after interruption");
                    }
                } catch (InterruptedException repeatedInterrupt) {
                    restoreInterrupt = true;
                    throw new IllegalStateException(
                            "Repeated interruption prevented exact child reap", repeatedInterrupt);
                }
            }
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void terminate(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Exact child process could not be reaped");
            }
        }
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        new java.security.SecureRandom().nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private record Capture(String text, boolean truncated) {}

    private record CommandResult(int exitCode, String output, boolean truncated) {}

    private record EngineIdentity(int uid, int gid) {}

    private record Collector(InputStream input, long limit) implements Callable<Capture> {
        @Override
        public Capture call() throws IOException {
            ByteArrayOutputStream retained = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            while (true) {
                int count = input.read(buffer);
                if (count < 0) {
                    break;
                }
                int keep = (int) Math.min(count, Math.max(0, limit - retained.size()));
                retained.write(buffer, 0, keep);
                total += count;
            }
            return new Capture(retained.toString(StandardCharsets.UTF_8), total > limit);
        }
    }
}
