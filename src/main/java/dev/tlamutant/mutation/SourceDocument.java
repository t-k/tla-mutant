package dev.tlamutant.mutation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tla2sany.st.Location;

public record SourceDocument(String content, List<Integer> lineStarts) {
    public SourceDocument {
        Objects.requireNonNull(content, "content");
        lineStarts = List.copyOf(lineStarts);
    }

    public static SourceDocument read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, content.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Source is not canonical UTF-8");
        }
        return fromString(content);
    }

    public static SourceDocument fromString(String content) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) == '\n') {
                starts.add(index + 1);
            }
        }
        return new SourceDocument(content, starts);
    }

    public SourceRange range(Location location) {
        if (location == null || location == Location.nullLoc) {
            throw new IllegalArgumentException("Semantic node has no source location");
        }
        int start = offset(location.beginLine(), location.beginColumn());
        int endExclusive = offset(location.endLine(), location.endColumn() + 1);
        return new SourceRange(start, endExclusive);
    }

    public String slice(SourceRange range) {
        validate(range);
        return content.substring(range.start(), range.endExclusive());
    }

    void validate(SourceRange range) {
        if (range.endExclusive() > content.length()) {
            throw new IllegalArgumentException("Source range exceeds the document");
        }
    }

    private int offset(int oneBasedLine, int oneBasedColumn) {
        if (oneBasedLine <= 0 || oneBasedLine > lineStarts.size() || oneBasedColumn <= 0) {
            throw new IllegalArgumentException("Invalid source location");
        }
        int offset = lineStarts.get(oneBasedLine - 1) + oneBasedColumn - 1;
        if (offset < 0 || offset > content.length()) {
            throw new IllegalArgumentException("Source location exceeds the document");
        }
        return offset;
    }
}
