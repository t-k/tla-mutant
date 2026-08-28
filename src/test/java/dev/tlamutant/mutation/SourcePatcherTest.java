package dev.tlamutant.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SourcePatcherTest {
    @Test
    void preservesEveryCharacterOutsideOneRangeIncludingCrLfAndUtf8() {
        SourceDocument document = SourceDocument.fromString("前置\r\nGuard == x < 3 \\* 注釈\r\n====\r\n");
        int start = document.content().indexOf("<");
        MutationCandidate candidate = MutationCandidate.create(
                MutationFamily.BRL, "Fixture", "Guard", new SourceRange(start, start + 1), "<", "<=");

        String patched = new SourcePatcher().apply(document, candidate).content();

        assertEquals("前置\r\nGuard == x <= 3 \\* 注釈\r\n====\r\n", patched);
    }

    @Test
    void rejectsEmptyMismatchedAndOutOfRangePatches() {
        SourceDocument document = SourceDocument.fromString("x < 3");
        assertThrows(IllegalArgumentException.class, () -> new SourcePatcher()
                .apply(
                        document,
                        MutationCandidate.create(
                                MutationFamily.BRL, "Fixture", "Guard", new SourceRange(2, 3), "<", "<")));
        assertThrows(IllegalArgumentException.class, () -> new SourcePatcher()
                .apply(
                        document,
                        MutationCandidate.create(
                                MutationFamily.BRL, "Fixture", "Guard", new SourceRange(0, 1), "<", ">")));
    }
}
