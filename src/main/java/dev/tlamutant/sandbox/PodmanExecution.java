package dev.tlamutant.sandbox;

public record PodmanExecution(
        String containerId, int exitCode, String stdout, String stderr, boolean timedOut, boolean outputTruncated) {}
