package com.event.producer.adapter.web.transaction;

import com.event.producer.domain.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JSON body received by the API to create a transaction. {@code userId} is the user who owns it. */
public record TransactionRequest(
        @NotNull UUID userId,
        @NotBlank String accountId,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal amount,
        @NotNull Instant occurredAt) {
}
