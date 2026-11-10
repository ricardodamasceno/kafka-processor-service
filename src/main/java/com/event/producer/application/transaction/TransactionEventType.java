package com.event.producer.application.transaction;

import com.event.producer.domain.transaction.TransactionStatus;

/**
 * What happened to a transaction. Consumers react to these types and never need to know how the change
 * was detected.
 */
public enum TransactionEventType {
    TRANSACTION_CREATED,
    TRANSACTION_PROCESSING,
    TRANSACTION_COMPLETED,
    TRANSACTION_FAILED;

    /** The event that announces that a transaction reached the given status. */
    public static TransactionEventType of(TransactionStatus status) {
        return switch (status) {
            case PENDING -> TRANSACTION_CREATED;
            case PROCESSING -> TRANSACTION_PROCESSING;
            case COMPLETED -> TRANSACTION_COMPLETED;
            case FAILED -> TRANSACTION_FAILED;
        };
    }
}
