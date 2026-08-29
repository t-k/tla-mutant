package dev.tlamutant.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

public final class BoundedFileSnapshot {
    private final byte[] bytes;
    private final String sha256;

    private BoundedFileSnapshot(byte[] bytes) {
        this.bytes = bytes.clone();
        this.sha256 = sha256(bytes);
    }

    public static BoundedFileSnapshot read(Path input, long maximumBytes) throws IOException {
        if (maximumBytes <= 0 || maximumBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Snapshot byte bound is outside the supported range");
        }
        Path path = input.toAbsolutePath().normalize();
        BasicFileAttributes attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Snapshot input must be a real regular file");
        }
        try (FileChannel channel = FileChannel.open(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(maximumBytes, 64 * 1024));
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            long total = 0;
            while (true) {
                int count = channel.read(buffer);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    continue;
                }
                total = Math.addExact(total, count);
                if (total > maximumBytes) {
                    throw new IllegalArgumentException("Snapshot input exceeds its byte bound");
                }
                output.write(buffer.array(), 0, count);
                buffer.clear();
            }
            if (total == 0) {
                throw new IllegalArgumentException("Snapshot input must not be empty");
            }
            return new BoundedFileSnapshot(output.toByteArray());
        }
    }

    public long sizeBytes() {
        return bytes.length;
    }

    public String sha256() {
        return sha256;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public <T> T parse(ObjectMapper mapper, Class<T> type) throws IOException {
        ObjectMapper strict = mapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        return strict.readValue(bytes, type);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
