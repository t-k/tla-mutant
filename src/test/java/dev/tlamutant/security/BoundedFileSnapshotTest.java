package dev.tlamutant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BoundedFileSnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void derivesSizeDigestAndStrictJsonFromOneBoundedByteSequence() throws Exception {
        byte[] bytes = "{\"value\":7}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path input = temporaryDirectory.resolve("input.json");
        Files.write(input, bytes);

        BoundedFileSnapshot snapshot = BoundedFileSnapshot.read(input, bytes.length);

        assertEquals(bytes.length, snapshot.sizeBytes());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), snapshot.sha256());
        assertEquals(7, snapshot.parse(new ObjectMapper(), Value.class).value());
    }

    @Test
    void rejectsOversizeSymlinkDuplicateKeysAndTrailingJson() throws IOException {
        Path exact = temporaryDirectory.resolve("exact.json");
        Files.writeString(exact, "{\"value\":7}");
        Path symlink = temporaryDirectory.resolve("link.json");
        Files.createSymbolicLink(symlink, exact.getFileName());
        Path duplicate = temporaryDirectory.resolve("duplicate.json");
        Files.writeString(duplicate, "{\"value\":7,\"value\":8}");
        Path trailing = temporaryDirectory.resolve("trailing.json");
        Files.writeString(trailing, "{\"value\":7} {}");

        assertThrows(IllegalArgumentException.class, () -> BoundedFileSnapshot.read(exact, 10));
        assertThrows(IllegalArgumentException.class, () -> BoundedFileSnapshot.read(symlink, 100));
        assertThrows(IOException.class, () -> BoundedFileSnapshot.read(duplicate, 100)
                .parse(new ObjectMapper(), Value.class));
        assertThrows(IOException.class, () -> BoundedFileSnapshot.read(trailing, 100)
                .parse(new ObjectMapper(), Value.class));
    }

    private record Value(int value) {}
}
