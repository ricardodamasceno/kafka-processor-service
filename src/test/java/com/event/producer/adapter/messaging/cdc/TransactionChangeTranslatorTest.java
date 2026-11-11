package com.event.producer.adapter.messaging.cdc;

import com.event.producer.adapter.messaging.InvalidMessageException;
import com.event.producer.adapter.messaging.cdc.TransactionChangeMessage.Row;
import com.event.producer.application.transaction.TransactionEvent;
import com.event.producer.application.transaction.TransactionEventType;
import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules that turn a row change into a transaction event, with no Kafka and no Spring. */
class TransactionChangeTranslatorTest {

    private static final UUID ID = UUID.fromString("3f2b8f36-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("a1111111-0000-4000-8000-000000000001");

    private final TransactionChangeTranslator translator = new TransactionChangeTranslator();

    private static Row row(TransactionStatus status) {
        return row(status, new BigDecimal("10.50"));
    }

    private static Row row(TransactionStatus status, BigDecimal amount) {
        return new Row(ID, USER_ID, "acc-1", TransactionType.DEPOSIT, amount, Instant.parse("2026-11-10T12:00:00Z"), status);
    }

    private static TransactionChangeMessage change(String op, Row before, Row after) {
        return new TransactionChangeMessage(op, before, after);
    }

    @Test
    void insertAsPendingIsACreatedEventWithTheCompleteState() {
        Optional<TransactionEvent> event = translator.translate(change("c", null, row(TransactionStatus.PENDING)));

        TransactionEvent created = event.orElseThrow();
        assertEquals(TransactionEventType.TRANSACTION_CREATED, created.type());
        assertEquals(ID, created.transaction().id());
        assertEquals(USER_ID, created.transaction().userId());
        assertEquals("acc-1", created.transaction().accountId());
        assertEquals(new BigDecimal("10.50"), created.transaction().amount());
        assertEquals(TransactionStatus.PENDING, created.transaction().status());
        assertEquals(ID + ":PENDING", created.id());
    }

    @Test
    void insertWithAnotherStatusIsIgnored() {
        assertTrue(translator.translate(change("c", null, row(TransactionStatus.COMPLETED))).isEmpty());
    }

    @Test
    void aStatusChangeBecomesTheEventOfTheNewStatus() {
        assertEvent(TransactionStatus.PENDING, TransactionStatus.PROCESSING, TransactionEventType.TRANSACTION_PROCESSING);
        assertEvent(TransactionStatus.PROCESSING, TransactionStatus.COMPLETED, TransactionEventType.TRANSACTION_COMPLETED);
        assertEvent(TransactionStatus.PROCESSING, TransactionStatus.FAILED, TransactionEventType.TRANSACTION_FAILED);
    }

    private void assertEvent(TransactionStatus from, TransactionStatus to, TransactionEventType expected) {
        TransactionEvent event = translator.translate(change("u", row(from), row(to))).orElseThrow();

        assertEquals(expected, event.type());
        assertEquals(to, event.transaction().status());
        assertEquals(USER_ID, event.transaction().userId());
        assertEquals(ID + ":" + to, event.id());
    }

    @Test
    void anUpdateThatKeepsTheStatusIsIgnored() {
        Row before = row(TransactionStatus.PENDING);
        Row after = row(TransactionStatus.PENDING, new BigDecimal("25.00"));

        assertTrue(translator.translate(change("u", before, after)).isEmpty());
    }

    @Test
    void movingBackToPendingIsIgnored() {
        assertTrue(translator.translate(change("u", row(TransactionStatus.PROCESSING), row(TransactionStatus.PENDING))).isEmpty());
    }

    @Test
    void deletesAndSnapshotReadsAreIgnored() {
        assertTrue(translator.translate(change("d", row(TransactionStatus.COMPLETED), null)).isEmpty());
        assertTrue(translator.translate(change("r", null, row(TransactionStatus.PENDING))).isEmpty());
    }

    @Test
    void anUpdateWithoutTheOldRowIsRejectedBecauseTheTableLacksReplicaIdentityFull() {
        assertThrows(InvalidMessageException.class,
                () -> translator.translate(change("u", null, row(TransactionStatus.COMPLETED))));
    }

    @Test
    void aChangeWithoutAnOperationIsRejected() {
        assertThrows(InvalidMessageException.class, () -> translator.translate(change(null, null, row(TransactionStatus.PENDING))));
    }

    @Test
    void anIncompleteRowIsRejected() {
        Row incomplete = new Row(ID, USER_ID, null, TransactionType.DEPOSIT, new BigDecimal("1.00"),
                Instant.parse("2026-11-10T12:00:00Z"), TransactionStatus.PENDING);

        assertThrows(InvalidMessageException.class, () -> translator.translate(change("c", null, incomplete)));
    }

    @Test
    void aRowWithoutTheUserIsRejected() {
        // Changes published before the user_id column existed do not have it
        Row withoutUser = new Row(ID, null, "acc-1", TransactionType.DEPOSIT, new BigDecimal("1.00"),
                Instant.parse("2026-11-10T12:00:00Z"), TransactionStatus.PENDING);

        assertThrows(InvalidMessageException.class, () -> translator.translate(change("c", null, withoutUser)));
    }

    @Test
    void aRowThatBreaksADomainRuleSurfacesTheDomainError() {
        Row invalid = row(TransactionStatus.PENDING, new BigDecimal("-1.00"));

        assertThrows(DomainValidationException.class, () -> translator.translate(change("c", null, invalid)));
    }
}
