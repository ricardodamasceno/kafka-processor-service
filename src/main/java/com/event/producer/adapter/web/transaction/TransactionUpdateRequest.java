package com.event.producer.adapter.web.transaction;

import com.event.producer.domain.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JSON body received by the API to update a transaction. The owner and the status cannot be changed by an update,
 * so they are not part of it.
 */
public record TransactionUpdateRequest(
        @NotBlank String accountId,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal amount,
        @NotNull Instant occurredAt) {
}
