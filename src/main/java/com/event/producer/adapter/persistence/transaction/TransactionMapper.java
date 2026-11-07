package com.event.producer.adapter.persistence.transaction;

import com.event.producer.domain.transaction.Transaction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Converts between the domain {@link Transaction} and the database {@link TransactionJpaEntity}.
 * Its only responsibility is this translation. Pure functions, so the methods are static and the
 * class is not a Spring bean.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TransactionMapper {

    static TransactionJpaEntity toEntity(Transaction transaction) {
        return new TransactionJpaEntity(transaction.id(), transaction.accountId(), transaction.type(),
                transaction.amount(), transaction.occurredAt());
    }

    static Transaction toDomain(TransactionJpaEntity entity) {
        return new Transaction(entity.getId(), entity.getAccountId(), entity.getType(),
                entity.getAmount(), entity.getOccurredAt());
    }
}
