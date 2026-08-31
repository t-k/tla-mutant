package dev.tlamutant.tlc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Callable;

record TlcOutputCollector(InputStream input, int limit) implements Callable<TlcOutputCollector.Capture> {
    TlcOutputCollector {
        Objects.requireNonNull(input, "input");
        if (limit <= 0) {
            throw new IllegalArgumentException("Output limit must be positive");
        }
    }

    @Override
    public Capture call() throws IOException {
        ByteArrayOutputStream retained = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        while (true) {
            int count = input.read(buffer);
            if (count < 0) {
                break;
            }
            int keep = (int) Math.min(count, Math.max(0, limit - retained.size()));
            retained.write(buffer, 0, keep);
            total += count;
        }
        return new Capture(retained.toString(StandardCharsets.UTF_8), total > limit);
    }

    record Capture(String text, boolean truncated) {}
}
