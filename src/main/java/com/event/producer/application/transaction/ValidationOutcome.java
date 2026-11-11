package com.event.producer.application.transaction;

/** What happened when a created transaction was validated. */
public enum ValidationOutcome {

    /** The user is active: the transaction moved to {@code PROCESSING}. */
    PROCESSING,

    /** The user is not active, or does not exist: the transaction moved to {@code FAILED}. */
    FAILED,

    /**
     * The transaction does not exist or is no longer {@code PENDING}, for example because an earlier delivery of the
     * same event already handled it. Nothing changed.
     */
    NOT_PENDING
}
