package com.event.producer.application.movement;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.domain.movement.Movement;
import com.event.producer.domain.movement.MovementRepository;
import com.event.producer.domain.movement.MovementType;
import com.event.producer.domain.shared.DomainValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovementServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Mock
    private MovementRepository repository;

    private MovementService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MovementService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void saveReturnsItsArgument() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordSavesTheMovementWithAnIdAndTheTimeFromTheClock() {
        saveReturnsItsArgument();

        Movement movement = service.record(userId, transactionId, TEN, MovementType.CREDIT);

        ArgumentCaptor<Movement> saved = ArgumentCaptor.forClass(Movement.class);
        verify(repository).save(saved.capture());
        assertEquals(movement, saved.getValue());
        assertEquals(userId, movement.userId());
        assertEquals(transactionId, movement.transactionId());
        assertEquals(TEN, movement.amount());
        assertEquals(MovementType.CREDIT, movement.type());
        assertEquals(NOW, movement.createdAt());
    }

    @Test
    void recordReturnsWhatTheRepositoryReturns() {
        Movement stored = new Movement(UUID.randomUUID(), userId, transactionId, TEN, MovementType.CREDIT, NOW);
        when(repository.save(any())).thenReturn(stored);

        assertSame(stored, service.record(userId, transactionId, TEN, MovementType.CREDIT));
    }

    @Test
    void eachMovementGetsItsOwnId() {
        saveReturnsItsArgument();

        Movement credit = service.record(userId, transactionId, TEN, MovementType.CREDIT);
        Movement debit = service.record(userId, transactionId, TEN, MovementType.DEBIT);

        assertNotEquals(credit.id(), debit.id());
        verify(repository, times(2)).save(any());
    }

    @Test
    void recordScalesTheAmountToTwoDecimals() {
        saveReturnsItsArgument();

        assertEquals(new BigDecimal("5.50"),
                service.record(userId, transactionId, new BigDecimal("5.5"), MovementType.DEBIT).amount());
    }

    @Test
    void recordRejectsAnInvalidAmountAndStoresNothing() {
        assertThrows(DomainValidationException.class,
                () -> service.record(userId, transactionId, BigDecimal.ZERO, MovementType.CREDIT));
        assertThrows(DomainValidationException.class,
                () -> service.record(userId, transactionId, new BigDecimal("-1.00"), MovementType.CREDIT));
        assertThrows(DomainValidationException.class,
                () -> service.record(userId, transactionId, new BigDecimal("1.001"), MovementType.CREDIT));

        verifyNoInteractions(repository);
    }

    @Test
    void aConflictFromTheRepositoryIsNotHidden() {
        // The unique index decides when a transaction already has a movement of the type; the adapter reports it
        when(repository.save(any())).thenThrow(new ConflictException("already has a movement"));

        assertThrows(ConflictException.class, () -> service.record(userId, transactionId, TEN, MovementType.CREDIT));
        verify(repository, times(1)).save(any());
    }
}
