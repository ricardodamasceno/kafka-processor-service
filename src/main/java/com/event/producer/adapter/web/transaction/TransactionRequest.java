package com.event.producer.adapter.web.transaction;

import com.event.producer.domain.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/** JSON body received by the API to create or update a transaction. */
public record TransactionRequest(
        @NotBlank String accountId,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal amount,
        @NotNull Instant occurredAt) {
}
