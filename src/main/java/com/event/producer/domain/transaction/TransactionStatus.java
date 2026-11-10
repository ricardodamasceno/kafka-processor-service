package com.event.producer.domain.transaction;

/**
 * Where a transaction is in its life. A new transaction is {@code PENDING}; the other states are
 * set as it is processed, ending in {@code COMPLETED} or {@code FAILED}.
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
