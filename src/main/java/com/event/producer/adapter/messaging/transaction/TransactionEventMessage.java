package com.event.producer.adapter.messaging.transaction;

import com.event.producer.application.transaction.TransactionEvent;
import com.event.producer.application.transaction.TransactionEventType;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The JSON shape of a transaction event on Kafka. It is the contract of the topics that consumers read,
 * and it does not depend on the columns of the table or on how the change was detected.
 *
 * @param eventId identifies the event; the same event always has the same id, so a consumer can ignore a duplicate
 * @param type    what happened
 */
public record TransactionEventMessage(String eventId, TransactionEventType type, TransactionData transaction) {

    /** The complete state of the transaction when the event happened. */
    public record TransactionData(UUID id, String accountId, TransactionType type, BigDecimal amount,
                                  Instant occurredAt, TransactionStatus status) {
    }

    static TransactionEventMessage from(TransactionEvent event) {
        Transaction transaction = event.transaction();
        return new TransactionEventMessage(event.id(), event.type(), new TransactionData(
                transaction.id(), transaction.accountId(), transaction.type(), transaction.amount(),
                transaction.occurredAt(), transaction.status()));
    }
}
