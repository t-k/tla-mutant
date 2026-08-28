package dev.tlamutant.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.regex.Pattern;

public record SafeRelativePath(String value) {
    private static final Pattern PORTABLE = Pattern.compile("[A-Za-z0-9_.][A-Za-z0-9._/-]*");

    public SafeRelativePath {
        Objects.requireNonNull(value, "value");
    }

    public static SafeRelativePath parse(String value) {
        if (value == null || value.isBlank() || !PORTABLE.matcher(value).matches()) {
            throw new IllegalArgumentException("Path is not in the portable allowlist");
        }
        if (value.startsWith("-") || value.startsWith("/") || value.endsWith("/") || value.contains("//")) {
            throw new IllegalArgumentException("Path is not a normalized relative path");
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.startsWith("-")) {
                throw new IllegalArgumentException("Path contains a forbidden segment");
            }
        }
        Path parsed = Path.of(value);
        if (parsed.isAbsolute() || !parsed.normalize().toString().equals(value)) {
            throw new IllegalArgumentException("Path is not normalized");
        }
        return new SafeRelativePath(value);
    }

    public Path resolveRegularFile(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate = normalizedRoot.resolve(value).normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Resolved path escapes the input root");
        }
        BasicFileAttributes attributes =
                Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || Files.isSymbolicLink(candidate)) {
            throw new IllegalArgumentException("Input path is not a regular file");
        }
        Object linkCount;
        try {
            linkCount = Files.getAttribute(candidate, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException exception) {
            throw new IllegalArgumentException("Filesystem cannot verify hard-link count", exception);
        }
        if (!(linkCount instanceof Number number) || number.longValue() != 1L) {
            throw new IllegalArgumentException("Input path must have exactly one hard link");
        }
        return candidate;
    }
}
