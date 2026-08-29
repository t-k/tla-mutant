package dev.tlamutant.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExperimentOutputValidator {
    private ExperimentOutputValidator() {}

    public static Outputs validate(Path json, Path markdown, List<Path> protectedFiles, List<Path> protectedDirectories)
            throws IOException {
        Path jsonTarget = canonicalOutput(json);
        Path markdownTarget = canonicalOutput(markdown);
        if (jsonTarget.equals(markdownTarget)) {
            throw new IllegalArgumentException("JSON and Markdown outputs require different paths");
        }
        Set<Path> protectedTargets = new HashSet<>();
        for (Path input : protectedFiles) {
            protectedTargets.add(input.toRealPath());
        }
        for (Path output : List.of(jsonTarget, markdownTarget)) {
            if (protectedTargets.contains(output)) {
                throw new IllegalArgumentException("Experiment output aliases a protected input");
            }
            for (Path directory : protectedDirectories) {
                if (output.startsWith(directory.toRealPath())) {
                    throw new IllegalArgumentException("Experiment output must not be inside a protected directory");
                }
            }
        }
        return new Outputs(jsonTarget, markdownTarget);
    }

    private static Path canonicalOutput(Path output) throws IOException {
        Path target = output.toAbsolutePath().normalize();
        if (target.getFileName() == null || Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("Experiment output path is invalid");
        }
        Files.createDirectories(target.getParent());
        return target.getParent().toRealPath().resolve(target.getFileName());
    }

    public record Outputs(Path json, Path markdown) {}
}
