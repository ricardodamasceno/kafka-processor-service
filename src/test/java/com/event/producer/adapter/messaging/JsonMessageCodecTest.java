package com.event.producer.adapter.messaging;

import com.event.producer.adapter.messaging.transaction.TransactionMessage;
import com.event.producer.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the JSON contract of the Kafka messages without starting Kafka or Spring. */
class JsonMessageCodecTest {

    private final JsonMessageCodec codec = new JsonMessageCodec(JsonMapper.builder().build());

    @Test
    void transactionMessageSurvivesARoundTrip() {
        TransactionMessage message = new TransactionMessage(UUID.randomUUID(), "acc-1", TransactionType.DEPOSIT,
                new BigDecimal("12.30"), Instant.parse("2026-11-07T10:00:00Z"));

        String json = codec.toJson(message);

        assertTrue(json.contains("\"occurredAt\":\"2026-11-07T10:00:00Z\""), json);
        assertEquals(message, codec.fromJson(json, TransactionMessage.class));
    }

    @Test
    void malformedJsonIsReportedAsInvalidMessage() {
        assertThrows(InvalidMessageException.class, () -> codec.fromJson("not json", TransactionMessage.class));
    }

    @Test
    void unknownEnumValueIsReportedAsInvalidMessage() {
        String json = "{\"id\":\"" + UUID.randomUUID() + "\",\"accountId\":\"a\",\"type\":\"BONUS\","
                + "\"amount\":1,\"occurredAt\":\"2026-11-07T10:00:00Z\"}";

        assertThrows(InvalidMessageException.class, () -> codec.fromJson(json, TransactionMessage.class));
    }
}
