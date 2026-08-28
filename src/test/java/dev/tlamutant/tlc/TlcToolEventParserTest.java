package dev.tlamutant.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TlcToolEventParserTest {
    @Test
    void parsesFramedMultilineToolMessagesAndIgnoresProse() {
        String output = "prose\n"
                + "@!@!@STARTMSG 2193:0 @!@!@\nModel checking completed.\nNo error found.\n@!@!@ENDMSG 2193 @!@!@\n";

        List<TlcToolEvent> events = new TlcToolEventParser().parse(output);

        assertEquals(List.of(new TlcToolEvent(2193, 0, "Model checking completed.\nNo error found.")), events);
    }

    @Test
    void ignoresTruncatedFrames() {
        assertEquals(List.of(), new TlcToolEventParser().parse("@!@!@STARTMSG 2193:0 @!@!@\npartial"));
    }
}
