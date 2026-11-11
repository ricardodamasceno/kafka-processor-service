package com.event.producer.domain.movement;

import com.event.producer.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementTypeTest {

    @Test
    void aWithdrawalIsADebit() {
        assertEquals(MovementType.DEBIT, MovementType.of(TransactionType.WITHDRAWAL));
    }

    @Test
    void aDepositIsACredit() {
        assertEquals(MovementType.CREDIT, MovementType.of(TransactionType.DEPOSIT));
    }
}
