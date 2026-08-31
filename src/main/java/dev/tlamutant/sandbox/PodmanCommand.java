package dev.tlamutant.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.config.ExecutionPolicy;
import dev.tlamutant.corpus.CorpusInputSnapshot;
import dev.tlamutant.experiment.OracleMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PodmanCommand {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                SandboxTask.EXPERIMENT,
                SandboxInput.legacy(List.of()));
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
                task,
                SandboxInput.legacy(List.of()));
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
            SandboxTask task,
            List<String> stateInvariants) {
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
                task,
                SandboxInput.legacy(stateInvariants));
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
            SandboxTask task,
            SandboxInput input) {
        if (task == null || input == null) {
            throw new IllegalArgumentException("Sandbox task and input are required");
        }
        if (task == SandboxTask.CENSUS && input.hasInvariants()) {
            throw new IllegalArgumentException("Census does not accept state-invariant arguments");
        }
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
        command.addAll(applicationArguments(task, module, config, limits, input));
        return List.copyOf(command);
    }

    private static List<String> applicationArguments(
            SandboxTask task, String module, String config, ExecutionPolicy.Limits limits, SandboxInput input) {
        List<String> common = List.of("--model-dir", "/input", "--module", module, "--config", config);
        List<String> arguments = new ArrayList<>();
        if (task == SandboxTask.CENSUS) {
            arguments.add("census");
            arguments.addAll(common);
            appendDerivedEdits(arguments, input);
            if (!input.derivedSourceEdits().isEmpty()) {
                arguments.addAll(List.of("--work-dir", "/scratch/work"));
            }
            return List.copyOf(arguments);
        }
        arguments.add("experiment");
        arguments.addAll(common);
        if (input.oracleMode() == OracleMode.FULL_PROPERTY) {
            arguments.addAll(List.of(
                    "--oracle-mode",
                    OracleMode.FULL_PROPERTY.cliName(),
                    "--manifest",
                    "/input/" + CorpusInputSnapshot.FULL_PROPERTY_MANIFEST));
        } else {
            for (String invariant : input.flatStateInvariants()) {
                arguments.add("--state-invariant");
                arguments.add(invariant);
            }
            appendInvariants(arguments, "--type-invariant", input.typeInvariants());
            appendInvariants(arguments, "--legacy-target-invariant", input.legacyTargetInvariants());
            appendInvariants(arguments, "--adopted-target-invariant", input.adoptedTargetInvariants());
        }
        appendDerivedEdits(arguments, input);
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

    private static void appendInvariants(List<String> arguments, String option, List<String> invariants) {
        for (String invariant : invariants) {
            arguments.add(option);
            arguments.add(invariant);
        }
    }

    private static void appendDerivedEdits(List<String> arguments, SandboxInput input) {
        for (var edit : input.derivedSourceEdits()) {
            arguments.add("--derived-source-edit-json");
            try {
                arguments.add(MAPPER.writeValueAsString(edit));
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Cannot encode derived source edit", exception);
            }
        }
    }
}
