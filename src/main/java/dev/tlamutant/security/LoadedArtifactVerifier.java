package dev.tlamutant.security;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class LoadedArtifactVerifier {
    private LoadedArtifactVerifier() {}

    public static void verify(BoundedFileSnapshot expected, Class<?>... loadedClasses) {
        Objects.requireNonNull(expected, "expected");
        if (loadedClasses == null || loadedClasses.length == 0) {
            throw new IllegalArgumentException("At least one loaded class is required");
        }
        for (Class<?> loadedClass : loadedClasses) {
            Path origin = origin(Objects.requireNonNull(loadedClass, "loadedClass"));
            try {
                BoundedFileSnapshot actual = BoundedFileSnapshot.read(origin, expected.sizeBytes());
                if (actual.sizeBytes() != expected.sizeBytes()
                        || !actual.sha256().equals(expected.sha256())) {
                    throw new IllegalArgumentException("Loaded class origin does not match the recorded artifact");
                }
            } catch (IOException | RuntimeException exception) {
                if (exception instanceof IllegalArgumentException illegalArgument) {
                    throw illegalArgument;
                }
                throw new IllegalArgumentException("Cannot verify loaded class origin", exception);
            }
        }
    }

    private static Path origin(Class<?> loadedClass) {
        try {
            Path path = Path.of(loadedClass
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException("Loaded class origin must be a real regular artifact");
            }
            return path;
        } catch (NullPointerException | URISyntaxException exception) {
            throw new IllegalArgumentException("Loaded class origin is unavailable", exception);
        }
    }
}
