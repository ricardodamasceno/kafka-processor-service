package com.event.producer.adapter.messaging.transaction;

import com.event.producer.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The JSON shape of a transaction event on Kafka. It is the contract between producer and consumer,
 * kept separate from the domain {@code Transaction} so the message format can evolve on its own.
 */
public record TransactionMessage(UUID id, String accountId, TransactionType type, BigDecimal amount, Instant occurredAt) {
}
