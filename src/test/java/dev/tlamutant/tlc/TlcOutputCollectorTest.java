package dev.tlamutant.tlc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

final class TlcOutputCollectorTest {
    @Test
    void retainsOnlyTheConfiguredByteLimitAndReportsTruncation() throws Exception {
        byte[] output = "0123456789".getBytes(UTF_8);

        TlcOutputCollector.Capture bounded = new TlcOutputCollector(new ByteArrayInputStream(output), 6).call();
        TlcOutputCollector.Capture exact =
                new TlcOutputCollector(new ByteArrayInputStream(output), output.length).call();

        assertEquals("012345", bounded.text());
        assertTrue(bounded.truncated());
        assertEquals("0123456789", exact.text());
        assertFalse(exact.truncated());
    }
}
