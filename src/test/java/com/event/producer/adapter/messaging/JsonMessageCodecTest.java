package com.event.producer.adapter.messaging;

import com.event.producer.adapter.messaging.cdc.TransactionChangeMessage;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks that the changes Debezium publishes can be read, without starting Kafka or Spring.
 * The samples have the shape of real messages: the row fields use the column names, {@code amount} is text,
 * the timestamp is ISO 8601 in UTC, and the envelope carries fields this service does not read.
 */
class JsonMessageCodecTest {

    private static final UUID ID = UUID.fromString("3f2b8f36-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("a1111111-0000-4000-8000-000000000001");

    private final JsonMessageCodec codec = new JsonMessageCodec(JsonMapper.builder().build());

    private static String row(String amount, String status) {
        return "{\"id\":\"" + ID + "\",\"user_id\":\"" + USER_ID + "\",\"account_id\":\"acc-1\",\"type\":\"DEPOSIT\","
                + "\"amount\":\"" + amount + "\",\"occurred_at\":\"2026-11-10T12:00:00.000000Z\",\"status\":\"" + status + "\"}";
    }

    private static String envelope(String op, String before, String after) {
        return "{\"before\":" + before + ",\"after\":" + after + ",\"source\":{\"db\":\"events_processor\"},"
                + "\"op\":\"" + op + "\",\"ts_ms\":1789000000000}";
    }

    @Test
    void readsAnInsert() {
        TransactionChangeMessage change = codec.fromJson(
                envelope("c", "null", row("10.50", "PENDING")), TransactionChangeMessage.class);

        assertEquals("c", change.op());
        assertNull(change.before());
        TransactionChangeMessage.Row after = change.after();
        assertEquals(ID, after.id());
        assertEquals(USER_ID, after.userId());
        assertEquals("acc-1", after.accountId());
        assertEquals(TransactionType.DEPOSIT, after.type());
        assertEquals(new BigDecimal("10.50"), after.amount());
        assertEquals(Instant.parse("2026-11-10T12:00:00Z"), after.occurredAt());
        assertEquals(TransactionStatus.PENDING, after.status());
        assertEquals(after, change.current());
    }

    @Test
    void readsAnUpdateWithTheOldAndTheNewRow() {
        TransactionChangeMessage change = codec.fromJson(
                envelope("u", row("10.50", "PENDING"), row("10.50", "COMPLETED")), TransactionChangeMessage.class);

        assertEquals("u", change.op());
        assertEquals(TransactionStatus.PENDING, change.before().status());
        assertEquals(TransactionStatus.COMPLETED, change.after().status());
        assertEquals(change.after(), change.current());
    }

    @Test
    void readsADeleteAndReportsTheDeletedRowAsCurrent() {
        TransactionChangeMessage change = codec.fromJson(
                envelope("d", row("10.50", "COMPLETED"), "null"), TransactionChangeMessage.class);

        assertEquals("d", change.op());
        assertNull(change.after());
        assertEquals(ID, change.current().id());
    }

    @Test
    void ignoresColumnsTheServiceDoesNotRead() {
        // A column added to the table shows up in the messages before the code reads it
        String after = row("10.50", "PENDING").replace("}", ",\"some_new_column\":\"x\"}");

        TransactionChangeMessage change = codec.fromJson(envelope("c", "null", after), TransactionChangeMessage.class);

        assertEquals(ID, change.after().id());
        assertEquals(TransactionStatus.PENDING, change.after().status());
    }

    @Test
    void aRowPublishedBeforeTheUserColumnExistedHasNoUser() {
        String withoutUser = row("10.50", "PENDING").replace("\"user_id\":\"" + USER_ID + "\",", "");

        TransactionChangeMessage change = codec.fromJson(envelope("c", "null", withoutUser), TransactionChangeMessage.class);

        assertNull(change.after().userId());
        assertEquals(ID, change.after().id());
    }

    @Test
    void malformedJsonIsReportedAsInvalidMessage() {
        assertThrows(InvalidMessageException.class, () -> codec.fromJson("not json", TransactionChangeMessage.class));
    }

    @Test
    void unknownEnumValueIsReportedAsInvalidMessage() {
        String json = envelope("c", "null", row("1.00", "PENDING").replace("DEPOSIT", "BONUS"));

        assertThrows(InvalidMessageException.class, () -> codec.fromJson(json, TransactionChangeMessage.class));
    }
}
