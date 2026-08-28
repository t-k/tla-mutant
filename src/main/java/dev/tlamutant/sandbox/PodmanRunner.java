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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class PodmanRunner {
    private static final String SECCOMP_SHA256 = "886ae167646b7e5db381ecf7c31e6de720a8e8da15cf3202fe1f67f424af2b75";

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
        Path input = inputDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(input) || Files.isSymbolicLink(input)) {
            throw new IllegalArgumentException("Input must be a real directory");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Sandbox timeout must be positive");
        }
        EngineIdentity identity = preflight();
        String name = "tla-mutant-" + randomHex(8);
        String scratchVolume = "tla-mutant-scratch-" + randomHex(8);
        String containerId = "";
        boolean volumeCreated = false;
        try {
            createScratchVolume(scratchVolume);
            volumeCreated = true;
            List<String> create = PodmanCommand.create(
                    name,
                    scratchVolume,
                    image,
                    input,
                    applicationJar,
                    toolsJar,
                    module,
                    config,
                    identity.uid(),
                    identity.gid(),
                    limits);
            containerId = runChecked(create).trim();
            if (!containerId.matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("Podman returned an invalid container identity");
            }
            verifyContainer(containerId, name);
            return startAttached(containerId, timeout);
        } finally {
            if (!containerId.isEmpty()) {
                cleanup(containerId);
            }
            if (volumeCreated) {
                cleanupVolume(scratchVolume);
            }
        }
    }

    private void createScratchVolume(String name) {
        String options = "size="
                + limits.fileBytes()
                + ",nr_inodes="
                + limits.fileCount()
                + ",uid="
                + 0
                + ",gid="
                + 0
                + ",mode=700";
        String created = runChecked(List.of(
                        "podman",
                        "volume",
                        "create",
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
        try {
            JsonNode inspect = mapper.readTree(runChecked(List.of("podman", "volume", "inspect", name)))
                    .get(0);
            if (!inspect.required("Name").textValue().equals(name)
                    || !inspect.at("/Options/type").asText().equals("tmpfs")
                    || !inspect.at("/Options").has("nocopy")
                    || !inspect.at("/Options/o").asText().equals(options)) {
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

    private void verifyContainer(String containerId, String expectedName) {
        try {
            JsonNode inspect = mapper.readTree(runChecked(List.of("podman", "inspect", containerId)))
                    .get(0);
            if (!inspect.required("Id").textValue().equals(containerId)
                    || !inspect.required("Name").textValue().equals(expectedName)) {
                throw new IllegalStateException("Created container identity mismatch");
            }
            JsonNode hostConfig = inspect.required("HostConfig");
            if (!hostConfig.required("ReadonlyRootfs").asBoolean()
                    || !hostConfig.required("NetworkMode").textValue().equals("none")
                    || hostConfig.required("Privileged").asBoolean()
                    || hostConfig.required("PidsLimit").longValue() != limits.pids()
                    || hostConfig.required("Memory").longValue() != limits.memoryBytes()) {
                throw new IllegalStateException("Podman did not preserve the requested resource boundary");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect created container", exception);
        }
    }

    private PodmanExecution startAttached(String containerId, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder("podman", "start", "--attach", containerId);
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<Capture> stdout = executor.submit(new Collector(process.getInputStream(), limits.stdoutBytes()));
                Future<Capture> stderr = executor.submit(new Collector(process.getErrorStream(), limits.stderrBytes()));
                boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!completed) {
                    runUnchecked(List.of("podman", "stop", "--time", "2", containerId));
                    if (!process.waitFor(3, TimeUnit.SECONDS)) {
                        runUnchecked(List.of("podman", "kill", containerId));
                        process.waitFor(5, TimeUnit.SECONDS);
                    }
                }
                Capture out = stdout.get();
                Capture err = stderr.get();
                return new PodmanExecution(
                        containerId,
                        completed ? process.exitValue() : -1,
                        out.text().trim(),
                        err.text(),
                        !completed,
                        out.truncated() || err.truncated());
            }
        } catch (IOException | InterruptedException | ExecutionException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Sandbox execution failed", exception);
        }
    }

    private void cleanup(String containerId) {
        runUnchecked(List.of("podman", "rm", "--force", "--time", "2", containerId));
        Process existence;
        try {
            existence = new ProcessBuilder("podman", "container", "exists", containerId).start();
            if (existence.waitFor() == 0) {
                throw new IllegalStateException("Sandbox container remained after cleanup");
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Cannot verify container cleanup", exception);
        }
    }

    private void cleanupVolume(String name) {
        runUnchecked(List.of("podman", "volume", "rm", "--force", name));
        try {
            Process existence = new ProcessBuilder("podman", "volume", "exists", name).start();
            if (existence.waitFor() == 0) {
                throw new IllegalStateException("Scratch volume remained after cleanup");
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Cannot verify scratch-volume cleanup", exception);
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
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("Command failed: " + command.get(0) + ": " + output);
            }
            return output;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Cannot execute " + command.get(0), exception);
        }
    }

    private static void runUnchecked(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).start();
            process.getOutputStream().close();
            process.getInputStream().transferTo(OutputStreamDiscard.INSTANCE);
            process.getErrorStream().transferTo(OutputStreamDiscard.INSTANCE);
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        new java.security.SecureRandom().nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private record Capture(String text, boolean truncated) {}

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

    private static final class OutputStreamDiscard extends java.io.OutputStream {
        private static final OutputStreamDiscard INSTANCE = new OutputStreamDiscard();

        @Override
        public void write(int ignored) {}
    }
}
