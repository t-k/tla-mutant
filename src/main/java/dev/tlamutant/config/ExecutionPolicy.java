package dev.tlamutant.config;

import java.util.Objects;

public record ExecutionPolicy(int version, String containerImage, Limits limits) {
    public ExecutionPolicy {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(containerImage, "containerImage");
        Objects.requireNonNull(limits, "limits");
    }

    public record Limits(
            long cpuSeconds,
            long memoryBytes,
            long pids,
            long openFiles,
            long fileBytes,
            long fileCount,
            long stdoutBytes,
            long stderrBytes,
            long states,
            long wallClockSeconds) {
        public Limits {
            if (cpuSeconds <= 0
                    || memoryBytes <= 0
                    || pids <= 0
                    || openFiles <= 0
                    || fileBytes <= 0
                    || fileCount <= 0
                    || stdoutBytes <= 0
                    || stderrBytes <= 0
                    || states <= 0
                    || wallClockSeconds <= 0) {
                throw new IllegalArgumentException("Every execution limit must be positive");
            }
        }
    }

    public static Limits enforceMaximums(Limits global, Limits requested) {
        if (global == null || requested == null) {
            throw new IllegalArgumentException("Global and requested execution limits are required");
        }
        requireAtMost("cpuSeconds", requested.cpuSeconds(), global.cpuSeconds());
        requireAtMost("memoryBytes", requested.memoryBytes(), global.memoryBytes());
        requireAtMost("pids", requested.pids(), global.pids());
        requireAtMost("openFiles", requested.openFiles(), global.openFiles());
        requireAtMost("fileBytes", requested.fileBytes(), global.fileBytes());
        requireAtMost("fileCount", requested.fileCount(), global.fileCount());
        requireAtMost("stdoutBytes", requested.stdoutBytes(), global.stdoutBytes());
        requireAtMost("stderrBytes", requested.stderrBytes(), global.stderrBytes());
        requireAtMost("states", requested.states(), global.states());
        requireAtMost("wallClockSeconds", requested.wallClockSeconds(), global.wallClockSeconds());
        return requested;
    }

    private static void requireAtMost(String name, long requested, long global) {
        if (requested > global) {
            throw new IllegalArgumentException(name + " exceeds the global execution policy");
        }
    }
}
