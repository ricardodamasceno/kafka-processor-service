package com.event.producer.adapter.messaging;

import com.event.producer.application.shared.PublishFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts message objects to and from JSON text. Messages travel as plain JSON strings, so the
 * Kafka serializers stay simple ({@code StringSerializer}) and the format is explicit and easy to read
 * in tools such as Kafka UI.
 */
@Component
@RequiredArgsConstructor
public class JsonMessageCodec {

    private final JsonMapper jsonMapper;

    public String toJson(Object message) {
        try {
            return jsonMapper.writeValueAsString(message);
        } catch (JacksonException e) {
            throw new PublishFailedException("Could not serialize message " + message.getClass().getSimpleName(), e);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new InvalidMessageException("Invalid " + type.getSimpleName() + " payload", e);
        }
    }
}
