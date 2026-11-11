package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.transaction.TransactionEvent;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the JSON that consumers of the transaction event topics will read. */
class TransactionEventMessageTest {

    private static final UUID ID = UUID.fromString("3f2b8f36-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("a1111111-0000-4000-8000-000000000001");

    private final JsonMessageCodec codec = new JsonMessageCodec(JsonMapper.builder().build());

    private static TransactionEventMessage messageFor(TransactionStatus status) {
        Transaction transaction = new Transaction(ID, USER_ID, "acc-1", TransactionType.DEPOSIT, new BigDecimal("10.50"),
                Instant.parse("2026-11-10T12:00:00Z"), status);
        return TransactionEventMessage.from(TransactionEvent.of(transaction));
    }

    @Test
    void theJsonCarriesTheEventIdTheTypeAndTheCompleteState() {
        String json = codec.toJson(messageFor(TransactionStatus.PENDING));

        assertTrue(json.contains("\"eventId\":\"" + ID + ":PENDING\""), json);
        assertTrue(json.contains("\"type\":\"TRANSACTION_CREATED\""), json);
        assertTrue(json.contains("\"id\":\"" + ID + "\""), json);
        assertTrue(json.contains("\"userId\":\"" + USER_ID + "\""), json);
        assertTrue(json.contains("\"accountId\":\"acc-1\""), json);
        assertTrue(json.contains("\"amount\":10.50"), json);
        assertTrue(json.contains("\"occurredAt\":\"2026-11-10T12:00:00Z\""), json);
        assertTrue(json.contains("\"status\":\"PENDING\""), json);
    }

    @Test
    void theTypeFollowsTheStatus() {
        assertTrue(codec.toJson(messageFor(TransactionStatus.PROCESSING)).contains("\"type\":\"TRANSACTION_PROCESSING\""));
        assertTrue(codec.toJson(messageFor(TransactionStatus.COMPLETED)).contains("\"type\":\"TRANSACTION_COMPLETED\""));
        assertTrue(codec.toJson(messageFor(TransactionStatus.FAILED)).contains("\"type\":\"TRANSACTION_FAILED\""));
    }

    @Test
    void aMessageSurvivesARoundTrip() {
        TransactionEventMessage message = messageFor(TransactionStatus.COMPLETED);

        assertEquals(message, codec.fromJson(codec.toJson(message), TransactionEventMessage.class));
    }
}
