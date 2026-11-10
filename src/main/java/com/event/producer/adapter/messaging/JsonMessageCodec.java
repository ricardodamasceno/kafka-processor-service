package com.event.producer.adapter.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads message objects from JSON text. Messages travel as plain JSON strings, so the Kafka
 * deserializers stay simple ({@code StringDeserializer}) and the format is explicit and easy to read
 * in tools such as Kafka UI.
 */
@Component
@RequiredArgsConstructor
public class JsonMessageCodec {

    private final JsonMapper jsonMapper;

    /** @throws InvalidMessageException when the text is not valid JSON or does not match the message type */
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new InvalidMessageException("Invalid " + type.getSimpleName() + " payload", e);
        }
    }
}
