package dev.tlamutant.tlc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TlcToolEventParser {
    private static final String START_MARKER = "@!@!@STARTMSG";
    private static final String END_MARKER = "@!@!@ENDMSG";
    private static final Pattern FRAME =
            Pattern.compile("@!@!@STARTMSG (\\d+):(\\d+) @!@!@\\R(.*?)\\R@!@!@ENDMSG \\1 @!@!@", Pattern.DOTALL);

    public ParseResult parse(String output) {
        Objects.requireNonNull(output, "output");
        List<TlcToolEvent> events = new ArrayList<>();
        Matcher matcher = FRAME.matcher(output);
        int cursor = 0;
        while (matcher.find()) {
            if (containsMarker(output.substring(cursor, matcher.start())) || containsMarker(matcher.group(3))) {
                return new ParseResult(List.of(), false);
            }
            try {
                events.add(new TlcToolEvent(
                        Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), matcher.group(3)));
            } catch (IllegalArgumentException exception) {
                return new ParseResult(List.of(), false);
            }
            cursor = matcher.end();
        }
        if (containsMarker(output.substring(cursor))) {
            return new ParseResult(List.of(), false);
        }
        return new ParseResult(events, true);
    }

    private static boolean containsMarker(String text) {
        return text.contains(START_MARKER) || text.contains(END_MARKER);
    }

    public record ParseResult(List<TlcToolEvent> events, boolean valid) {
        public ParseResult {
            events = List.copyOf(events);
            if (!valid && !events.isEmpty()) {
                throw new IllegalArgumentException("Invalid TLC output cannot expose partial events");
            }
        }
    }
}
