package com.event.producer.domain.transaction;

/**
 * Where a transaction is in its life. A new transaction is {@code PENDING}; the other states are
 * set as it is processed, ending in {@code COMPLETED} or {@code FAILED}.
 * <ul>
 *   <li>{@code PENDING}: created and waiting for validation.</li>
 *   <li>{@code PROCESSING}: validated; the transaction is being carried out.</li>
 *   <li>{@code COMPLETED} and {@code FAILED}: final.</li>
 * </ul>
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    /**
     * Whether a transaction in this status may move to {@code next}: a pending transaction is validated and goes to
     * processing, or fails; a transaction in processing completes or fails; {@code COMPLETED} and {@code FAILED}
     * are final.
     */
    public boolean canMoveTo(TransactionStatus next) {
        return switch (this) {
            case PENDING -> next == PROCESSING || next == FAILED;
            case PROCESSING -> next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
