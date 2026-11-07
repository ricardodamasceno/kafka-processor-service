package com.event.producer.adapter.web.transaction;

import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JSON body returned by the API for a transaction. */
public record TransactionResponse(UUID id, String accountId, TransactionType type, BigDecimal amount, Instant occurredAt) {

    static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.id(), transaction.accountId(), transaction.type(),
                transaction.amount(), transaction.occurredAt());
    }
}
