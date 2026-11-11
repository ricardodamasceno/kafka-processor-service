package com.event.producer.adapter.persistence.transaction;

import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionMapperTest {

    @Test
    void convertsDomainToEntityAndBack() {
        Transaction transaction = new Transaction(UUID.randomUUID(), UUID.randomUUID(), "acc-1",
                TransactionType.WITHDRAWAL, new BigDecimal("42.50"), Instant.parse("2026-11-07T10:00:00Z"),
                TransactionStatus.COMPLETED);

        TransactionJpaEntity entity = TransactionMapper.toEntity(transaction);

        assertEquals(transaction.id(), entity.getId());
        assertEquals(transaction.userId(), entity.getUserId());
        assertEquals(transaction.accountId(), entity.getAccountId());
        assertEquals(transaction.type(), entity.getType());
        assertEquals(transaction.amount(), entity.getAmount());
        assertEquals(transaction.occurredAt(), entity.getOccurredAt());
        assertEquals(transaction.status(), entity.getStatus());
        assertEquals(transaction, TransactionMapper.toDomain(entity));
    }
}
