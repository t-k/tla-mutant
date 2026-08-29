package dev.tlamutant.sandbox;

public record PodmanExecution(
        String containerId,
        String scratchVolume,
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean outputTruncated) {}
