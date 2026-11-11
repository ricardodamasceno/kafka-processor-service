package com.event.producer.domain.notification;

import lombok.NonNull;

import java.util.UUID;

/**
 * A message for a user about how one of their transactions ended.
 * <p>
 * The notification context does not use the classes of the transaction context: it only knows the ids and the outcome,
 * which is all that is needed to tell the user. In a real system it would be a separate service.
 */
public record Notification(@NonNull UUID userId, @NonNull UUID transactionId, @NonNull TransactionOutcome outcome) {
}
