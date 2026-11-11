package com.event.producer.application.movement;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.domain.movement.Movement;
import com.event.producer.domain.movement.MovementType;
import com.event.producer.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final BigDecimal TEN = new BigDecimal("10.00");

    private final InMemoryMovementRepository repository = new InMemoryMovementRepository();
    private final MovementService service = new MovementService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    @Test
    void recordSavesTheMovementWithAnIdAndTheTimeFromTheClock() {
        Movement movement = service.record(userId, transactionId, TEN, MovementType.CREDIT);

        assertEquals(userId, movement.userId());
        assertEquals(transactionId, movement.transactionId());
        assertEquals(TEN, movement.amount());
        assertEquals(MovementType.CREDIT, movement.type());
        assertEquals(NOW, movement.createdAt());
        assertEquals(movement, repository.store.getFirst());
    }

    @Test
    void eachMovementGetsItsOwnId() {
        Movement credit = service.record(userId, transactionId, TEN, MovementType.CREDIT);
        Movement debit = service.record(userId, transactionId, TEN, MovementType.DEBIT);

        assertNotEquals(credit.id(), debit.id());
    }

    @Test
    void recordScalesTheAmountToTwoDecimals() {
        assertEquals(new BigDecimal("5.50"), service.record(userId, transactionId, new BigDecimal("5.5"), MovementType.DEBIT).amount());
    }

    @Test
    void recordRejectsAnInvalidAmountAndStoresNothing() {
        assertThrows(DomainValidationException.class,
                () -> service.record(userId, transactionId, BigDecimal.ZERO, MovementType.CREDIT));
        assertThrows(DomainValidationException.class,
                () -> service.record(userId, transactionId, new BigDecimal("-1.00"), MovementType.CREDIT));
        assertThrows(DomainValidationException.class,
                () -> service.record(userId, transactionId, new BigDecimal("1.001"), MovementType.CREDIT));
        assertTrue(repository.store.isEmpty());
    }

    @Test
    void aTransactionCannotHaveTwoMovementsOfTheSameType() {
        service.record(userId, transactionId, TEN, MovementType.CREDIT);

        assertThrows(ConflictException.class, () -> service.record(userId, transactionId, TEN, MovementType.CREDIT));
        assertEquals(1, repository.store.size());
    }
}
