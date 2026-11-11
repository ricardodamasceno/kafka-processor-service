package com.event.producer.domain.transaction;

import com.event.producer.domain.shared.DomainValidationException;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A financial transaction on an account, made by a user.
 * <p>
 * Domain entity: an immutable record. Money uses {@link BigDecimal} (never double), and the
 * amount is always positive: the direction of the movement comes from {@link TransactionType}.
 * Null checks come from Lombok's {@code @NonNull}; the business rules stay in the constructor.
 * The {@link TransactionStatus} says where the transaction is in its life.
 */
public record Transaction(
        @NonNull UUID id,
        @NonNull UUID userId,
        @NonNull String accountId,
        @NonNull TransactionType type,
        @NonNull BigDecimal amount,
        @NonNull Instant occurredAt,
        @NonNull TransactionStatus status) {

    public Transaction {
        if (accountId.isBlank()) {
            throw new DomainValidationException("accountId must not be blank");
        }
        if (amount.signum() <= 0) {
            throw new DomainValidationException("amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new DomainValidationException("amount must have at most 2 decimal places");
        }
        amount = amount.setScale(2);
    }
}
