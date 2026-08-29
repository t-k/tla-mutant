package dev.tlamutant.sandbox;

import dev.tlamutant.config.ExecutionPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PodmanCommand {
    private PodmanCommand() {}

    public static List<String> create(
            String containerName,
            String runToken,
            String scratchVolume,
            String image,
            Path inputDirectory,
            Path applicationJar,
            Path toolsJar,
            String module,
            String config,
            int uid,
            int gid,
            ExecutionPolicy.Limits limits) {
        return create(
                containerName,
                runToken,
                scratchVolume,
                image,
                inputDirectory,
                applicationJar,
                toolsJar,
                module,
                config,
                uid,
                gid,
                limits,
                SandboxTask.EXPERIMENT);
    }

    public static List<String> create(
            String containerName,
            String runToken,
            String scratchVolume,
            String image,
            Path inputDirectory,
            Path applicationJar,
            Path toolsJar,
            String module,
            String config,
            int uid,
            int gid,
            ExecutionPolicy.Limits limits,
            SandboxTask task) {
        Objects.requireNonNull(task, "task");
        List<String> command = new ArrayList<>(List.of(
                "podman",
                "create",
                "--name",
                containerName,
                "--label",
                "dev.tlamutant.run=" + runToken,
                "--pull",
                "never",
                "--network",
                "none",
                "--read-only",
                "--read-only-tmpfs=false",
                "--cap-drop",
                "ALL",
                "--security-opt",
                "no-new-privileges",
                "--security-opt",
                "seccomp=/usr/share/containers/seccomp.json",
                "--userns",
                "keep-id",
                "--user",
                uid + ":" + gid,
                "--pids-limit",
                Long.toString(limits.pids()),
                "--memory",
                Long.toString(limits.memoryBytes()),
                "--memory-swap",
                Long.toString(limits.memoryBytes()),
                "--cpus",
                "1",
                "--ulimit",
                "nofile=" + limits.openFiles() + ":" + limits.openFiles(),
                "--ulimit",
                "cpu=" + limits.cpuSeconds() + ":" + limits.cpuSeconds(),
                "--ulimit",
                "fsize=" + limits.fileBytes() + ":" + limits.fileBytes(),
                "--mount",
                "type=volume,source=" + scratchVolume + ",destination=/scratch",
                "--mount",
                "type=bind,source=" + inputDirectory.toAbsolutePath().normalize() + ",destination=/input,ro=true",
                "--mount",
                "type=bind,source=" + applicationJar.toAbsolutePath().normalize() + ",destination=/opt/app.jar,ro=true",
                "--mount",
                "type=bind,source=" + toolsJar.toAbsolutePath().normalize() + ",destination=/opt/tla2tools.jar,ro=true",
                "--env",
                "HOME=/scratch",
                "--env",
                "TMPDIR=/scratch",
                "--env",
                "LANG=C.UTF-8",
                "--workdir",
                "/scratch",
                "--entrypoint",
                "java",
                image,
                "-XX:+UseParallelGC",
                "-Djava.io.tmpdir=/scratch",
                "-cp",
                "/opt/app.jar:/opt/tla2tools.jar",
                "dev.tlamutant.cli.Main"));
        command.addAll(applicationArguments(task, module, config, limits));
        return List.copyOf(command);
    }

    private static List<String> applicationArguments(
            SandboxTask task, String module, String config, ExecutionPolicy.Limits limits) {
        List<String> common = List.of("--model-dir", "/input", "--module", module, "--config", config);
        List<String> arguments = new ArrayList<>();
        if (task == SandboxTask.CENSUS) {
            arguments.add("census");
            arguments.addAll(common);
            return List.copyOf(arguments);
        }
        arguments.add("experiment");
        arguments.addAll(common);
        arguments.addAll(List.of(
                "--tools-jar",
                "/opt/tla2tools.jar",
                "--work-dir",
                "/scratch/work",
                "--timeout-seconds",
                Long.toString(limits.wallClockSeconds()),
                "--max-states",
                Long.toString(limits.states()),
                "--max-state-bytes",
                Long.toString(limits.fileBytes())));
        return List.copyOf(arguments);
    }
}
