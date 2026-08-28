package dev.tlamutant.tlc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TlcToolEventParser {
    private static final Pattern FRAME =
            Pattern.compile("@!@!@STARTMSG (\\d+):(\\d+) @!@!@\\R(.*?)\\R@!@!@ENDMSG \\1 @!@!@", Pattern.DOTALL);

    public List<TlcToolEvent> parse(String output) {
        Objects.requireNonNull(output, "output");
        List<TlcToolEvent> events = new ArrayList<>();
        Matcher matcher = FRAME.matcher(output);
        while (matcher.find()) {
            events.add(new TlcToolEvent(
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), matcher.group(3)));
        }
        return List.copyOf(events);
    }
}
