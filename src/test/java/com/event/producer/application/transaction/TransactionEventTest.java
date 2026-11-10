package com.event.producer.application.transaction;

import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TransactionEventTest {

    private static final UUID ID = UUID.fromString("3f2b8f36-0000-4000-8000-000000000001");

    private static Transaction transaction(TransactionStatus status) {
        return new Transaction(ID, "acc-1", TransactionType.DEPOSIT, new BigDecimal("10.00"),
                Instant.parse("2026-11-10T12:00:00Z"), status);
    }

    @Test
    void theTypeFollowsTheStatusOfTheTransaction() {
        assertEquals(TransactionEventType.TRANSACTION_CREATED, TransactionEvent.of(transaction(TransactionStatus.PENDING)).type());
        assertEquals(TransactionEventType.TRANSACTION_PROCESSING, TransactionEvent.of(transaction(TransactionStatus.PROCESSING)).type());
        assertEquals(TransactionEventType.TRANSACTION_COMPLETED, TransactionEvent.of(transaction(TransactionStatus.COMPLETED)).type());
        assertEquals(TransactionEventType.TRANSACTION_FAILED, TransactionEvent.of(transaction(TransactionStatus.FAILED)).type());
    }

    @Test
    void theIdIsTheSameForTheSameTransactionAndStatusAndDiffersBetweenStatuses() {
        assertEquals(TransactionEvent.of(transaction(TransactionStatus.COMPLETED)).id(),
                TransactionEvent.of(transaction(TransactionStatus.COMPLETED)).id());
        assertNotEquals(TransactionEvent.of(transaction(TransactionStatus.PROCESSING)).id(),
                TransactionEvent.of(transaction(TransactionStatus.COMPLETED)).id());
    }
}
