package com.event.producer.domain.movement;

import com.event.producer.domain.shared.DomainValidationException;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A movement of money in the account of a user, caused by a transaction.
 * <p>
 * Domain entity: an immutable record. Money uses {@link BigDecimal} (never double), and the amount is always
 * positive: the direction comes from {@link MovementType}. Null checks come from Lombok's {@code @NonNull}; the
 * business rules stay in the constructor.
 */
public record Movement(
        @NonNull UUID id,
        @NonNull UUID userId,
        @NonNull UUID transactionId,
        @NonNull BigDecimal amount,
        @NonNull MovementType type,
        @NonNull Instant createdAt) {

    public Movement {
        if (amount.signum() <= 0) {
            throw new DomainValidationException("amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new DomainValidationException("amount must have at most 2 decimal places");
        }
        amount = amount.setScale(2);
    }
}
