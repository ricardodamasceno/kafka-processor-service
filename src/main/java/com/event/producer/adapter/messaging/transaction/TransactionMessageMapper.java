package com.event.producer.adapter.messaging.transaction;

import com.event.producer.domain.transaction.Transaction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Converts between the domain {@link Transaction} and the Kafka {@link TransactionMessage}. Pure functions, so static. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TransactionMessageMapper {

    static TransactionMessage toMessage(Transaction transaction) {
        return new TransactionMessage(transaction.id(), transaction.accountId(), transaction.type(),
                transaction.amount(), transaction.occurredAt());
    }

    /** Rebuilds the domain object, so the domain rules are checked again on the consumer side. */
    static Transaction toDomain(TransactionMessage message) {
        return new Transaction(message.id(), message.accountId(), message.type(),
                message.amount(), message.occurredAt());
    }
}
