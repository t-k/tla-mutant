package dev.tlamutant.tlc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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
        byte[] bytes = retained.toByteArray();
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
            return new Capture(text, total > limit, true);
        } catch (CharacterCodingException exception) {
            return new Capture(new String(bytes, StandardCharsets.UTF_8), total > limit, false);
        }
    }

    record Capture(String text, boolean truncated, boolean validUtf8) {
        static Capture empty() {
            return new Capture("", false, true);
        }
    }
}
