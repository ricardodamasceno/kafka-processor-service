package com.event.producer.domain.movement;

import com.event.producer.domain.transaction.TransactionType;

/** The direction of a movement: a credit adds money to the account of the user, a debit takes it. */
public enum MovementType {
    CREDIT,
    DEBIT;

    /** The movement a transaction causes: a withdrawal takes money (debit), anything else adds it (credit). */
    public static MovementType of(TransactionType transactionType) {
        return transactionType == TransactionType.WITHDRAWAL ? DEBIT : CREDIT;
    }
}
