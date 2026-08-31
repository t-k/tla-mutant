package dev.tlamutant.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tla2sany.semantic.SemanticNode;

final class LoadedArtifactVerifierTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsARecordedArtifactThatDidNotSupplyTheLoadedClass() throws Exception {
        Path claimed = temporaryDirectory.resolve("claimed-tools.jar");
        Files.writeString(claimed, "not the loaded tools artifact", UTF_8);

        BoundedFileSnapshot expected = BoundedFileSnapshot.read(claimed, 1024);

        assertThrows(IllegalArgumentException.class, () -> LoadedArtifactVerifier.verify(expected, SemanticNode.class));
    }
}
