package dev.tlamutant.experiment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FullPropertyConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesTheCompleteConfigurationByteForByte() throws Exception {
        byte[] bytes = ("\\* keep this comment\r\n"
                        + "SPECIFICATION Spec\r\n"
                        + "INVARIANTS TypeOK Safe\r\n"
                        + "PROPERTIES Live EventuallyDone\r\n")
                .getBytes(UTF_8);
        Path source = temporaryDirectory.resolve("Spec.cfg");
        Path target = temporaryDirectory.resolve("frozen.cfg");
        Files.write(source, bytes);

        FullPropertyConfig.Materialized materialized = FullPropertyConfig.write(source, target);

        assertEquals(target.toAbsolutePath().normalize(), materialized.path());
        assertArrayEquals(bytes, Files.readAllBytes(target));
        assertEquals(bytes.length, materialized.snapshot().sizeBytes());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                materialized.snapshot().sha256());
    }

    @Test
    void refusesToOverwriteAnExistingEvidenceFile() throws Exception {
        Path source = temporaryDirectory.resolve("Spec.cfg");
        Path target = temporaryDirectory.resolve("frozen.cfg");
        Files.writeString(source, "SPECIFICATION Spec\n", UTF_8);
        Files.writeString(target, "existing\n", UTF_8);

        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> FullPropertyConfig.write(source, target));
        assertEquals("existing\n", Files.readString(target, UTF_8));
    }
}
