package com.event.producer.domain;

import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Domain tests need no Spring and no database: they run in milliseconds. */
class TransactionTest {

    private static Transaction with(String accountId, TransactionType type, BigDecimal amount) {
        return new Transaction(UUID.randomUUID(), accountId, type, amount, Instant.parse("2026-11-07T10:00:00Z"));
    }

    @Test
    void normalizesAmountToTwoDecimalPlaces() {
        Transaction transaction = with("acc-1", TransactionType.DEPOSIT, new BigDecimal("10"));

        assertEquals(new BigDecimal("10.00"), transaction.amount());
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThrows(DomainValidationException.class, () -> with("acc-1", TransactionType.DEPOSIT, BigDecimal.ZERO));
        assertThrows(DomainValidationException.class, () -> with("acc-1", TransactionType.DEPOSIT, new BigDecimal("-1")));
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThrows(DomainValidationException.class, () -> with("acc-1", TransactionType.DEPOSIT, new BigDecimal("1.005")));
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimalPlaces() {
        Transaction transaction = with("acc-1", TransactionType.WITHDRAWAL, new BigDecimal("1.500"));

        assertEquals(new BigDecimal("1.50"), transaction.amount());
    }

    @Test
    void rejectsBlankAccount() {
        assertThrows(DomainValidationException.class, () -> with(" ", TransactionType.DEPOSIT, BigDecimal.ONE));
    }

    @Test
    void rejectsNullFields() {
        // Lombok's @NonNull makes the constructor throw NullPointerException
        assertThrows(NullPointerException.class, () -> with("acc-1", null, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> with("acc-1", TransactionType.DEPOSIT, null));
        assertThrows(NullPointerException.class, () -> with(null, TransactionType.DEPOSIT, BigDecimal.ONE));
    }
}
