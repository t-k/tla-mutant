package dev.tlamutant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

final class StrictJsonTest {
    @Test
    void rejectsDuplicateKeysTrailingTokensAndNullPrimitives() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertEquals(1, StrictJson.parse(mapper, "{\"value\":1}", Value.class).value());
        assertThrows(
                JsonProcessingException.class,
                () -> StrictJson.parse(mapper, "{\"value\":1,\"value\":2}", Value.class));
        assertThrows(JsonProcessingException.class, () -> StrictJson.parse(mapper, "{\"value\":1} {}", Value.class));
        assertThrows(JsonProcessingException.class, () -> StrictJson.parse(mapper, "{\"value\":1}\0", Value.class));
        assertThrows(JsonProcessingException.class, () -> StrictJson.parse(mapper, "{\"value\":null}", Value.class));
    }

    private record Value(int value) {}
}
