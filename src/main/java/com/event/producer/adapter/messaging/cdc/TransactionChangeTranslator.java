package com.event.producer.adapter.messaging.cdc;

import com.event.producer.adapter.messaging.InvalidMessageException;
import com.event.producer.application.transaction.TransactionEvent;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns a row change of the {@code transactions} table into a transaction event, or into nothing when the change
 * is not something consumers care about.
 * <p>
 * This is the only class that interprets {@code before} and {@code after}. Everything downstream works with
 * {@link TransactionEvent}, so changing how changes are captured only changes this package.
 * <table>
 *   <caption>Rules</caption>
 *   <tr><th>Change</th><th>Event</th></tr>
 *   <tr><td>insert with status {@code PENDING}</td><td>{@code TRANSACTION_CREATED}</td></tr>
 *   <tr><td>update that changed the status to {@code PROCESSING}, {@code COMPLETED} or {@code FAILED}</td>
 *       <td>the event of that status</td></tr>
 *   <tr><td>any other insert or update (for example a changed amount), a delete, a snapshot read</td>
 *       <td>none</td></tr>
 * </table>
 */
@Component
class TransactionChangeTranslator {

    Optional<TransactionEvent> translate(TransactionChangeMessage change) {
        String op = change.op();
        if (op == null) {
            throw new InvalidMessageException("Change without an operation", null);
        }
        return switch (op) {
            case "c" -> created(change.after());
            case "u" -> statusChanged(change.before(), change.after());
            default -> Optional.empty();
        };
    }

    private static Optional<TransactionEvent> created(TransactionChangeMessage.Row after) {
        if (after == null || after.status() != TransactionStatus.PENDING) {
            return Optional.empty();
        }
        return Optional.of(TransactionEvent.of(toTransaction(after)));
    }

    private static Optional<TransactionEvent> statusChanged(TransactionChangeMessage.Row before,
                                                            TransactionChangeMessage.Row after) {
        if (before == null || after == null) {
            // Without the old row the status change cannot be seen: the table is missing REPLICA IDENTITY FULL.
            throw new InvalidMessageException("Update without the old or the new row: the table needs REPLICA IDENTITY FULL", null);
        }
        if (after.status() == before.status() || after.status() == TransactionStatus.PENDING) {
            return Optional.empty();
        }
        return Optional.of(TransactionEvent.of(toTransaction(after)));
    }

    private static Transaction toTransaction(TransactionChangeMessage.Row row) {
        if (row.id() == null || row.userId() == null || row.accountId() == null || row.type() == null
                || row.amount() == null || row.occurredAt() == null || row.status() == null) {
            throw new InvalidMessageException("Incomplete transaction row: " + row, null);
        }
        return new Transaction(row.id(), row.userId(), row.accountId(), row.type(), row.amount(),
                row.occurredAt(), row.status());
    }
}
