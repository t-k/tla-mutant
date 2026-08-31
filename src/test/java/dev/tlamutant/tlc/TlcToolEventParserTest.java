package dev.tlamutant.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TlcToolEventParserTest {
    @Test
    void parsesFramedMultilineToolMessagesAndIgnoresProse() {
        String output = "prose\n"
                + "@!@!@STARTMSG 2193:0 @!@!@\nModel checking completed.\nNo error found.\n@!@!@ENDMSG 2193 @!@!@\n";

        TlcToolEventParser.ParseResult result = new TlcToolEventParser().parse(output);

        assertTrue(result.valid());
        assertEquals(List.of(new TlcToolEvent(2193, 0, "Model checking completed.\nNo error found.")), result.events());
    }

    @Test
    void rejectsTruncatedMismatchedAndStrayFrames() {
        for (String output : List.of(
                "@!@!@STARTMSG 2193:0 @!@!@\npartial",
                "@!@!@STARTMSG 2193:0 @!@!@\ncomplete\n@!@!@ENDMSG 2116 @!@!@\n",
                "prose\n@!@!@ENDMSG 2193 @!@!@\n")) {
            TlcToolEventParser.ParseResult result = new TlcToolEventParser().parse(output);

            assertFalse(result.valid(), output);
            assertEquals(List.of(), result.events(), output);
        }
    }
}
