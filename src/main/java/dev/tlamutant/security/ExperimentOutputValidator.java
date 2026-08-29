package dev.tlamutant.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ExperimentOutputValidator {
    private ExperimentOutputValidator() {}

    public static Outputs validate(Path json, Path markdown, List<Path> protectedFiles, List<Path> protectedDirectories)
            throws IOException {
        Path jsonTarget = canonicalOutput(json);
        Path markdownTarget = canonicalOutput(markdown);
        if (aliases(jsonTarget, markdownTarget)) {
            throw new IllegalArgumentException("JSON and Markdown outputs require different paths");
        }
        Set<Path> protectedTargets = new HashSet<>();
        for (Path input : protectedFiles) {
            protectedTargets.add(input.toRealPath());
        }
        for (Path output : List.of(jsonTarget, markdownTarget)) {
            for (Path protectedTarget : protectedTargets) {
                if (aliases(output, protectedTarget)) {
                    throw new IllegalArgumentException("Experiment output aliases a protected input");
                }
            }
            for (Path directory : protectedDirectories) {
                if (portableStartsWith(output, directory.toRealPath())) {
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

    private static boolean aliases(Path left, Path right) throws IOException {
        if (portableEquals(left, right)) {
            return true;
        }
        return Files.exists(left) && Files.exists(right) && Files.isSameFile(left, right);
    }

    private static boolean portableStartsWith(Path path, Path prefix) {
        if (!portableEquals(path.getRoot(), prefix.getRoot()) || path.getNameCount() < prefix.getNameCount()) {
            return false;
        }
        for (int index = 0; index < prefix.getNameCount(); index++) {
            if (!portableEquals(path.getName(index), prefix.getName(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean portableEquals(Path left, Path right) {
        if (left == null || right == null) {
            return left == right;
        }
        return portableName(left).equals(portableName(right));
    }

    private static String portableName(Path path) {
        return Normalizer.normalize(path.toString(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public record Outputs(Path json, Path markdown) {}
}
