package com.event.producer.application.transaction;

import com.event.producer.domain.transaction.Transaction;
import lombok.NonNull;

/**
 * Something that happened to a transaction, with the complete state of the transaction at that moment.
 * Carrying the whole state means a consumer never needs the previous one.
 */
public record TransactionEvent(@NonNull TransactionEventType type, @NonNull Transaction transaction) {

    /** The event for the status the transaction is in. */
    public static TransactionEvent of(Transaction transaction) {
        return new TransactionEvent(TransactionEventType.of(transaction.status()), transaction);
    }

    /**
     * Identifies the event. A transaction reaches each status once, so the pair (transaction, status) is unique
     * and stays the same if the same event is produced twice. Consumers use it to ignore duplicates.
     */
    public String id() {
        return transaction.id() + ":" + transaction.status();
    }
}
