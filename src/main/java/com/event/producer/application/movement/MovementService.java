package com.event.producer.application.movement;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.domain.movement.Movement;
import com.event.producer.domain.movement.MovementRepository;
import com.event.producer.domain.movement.MovementType;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/**
 * Application service for movements. No Spring annotations; it is wired in {@code config.ServiceConfig}.
 * <p>
 * Nothing calls it yet: it will be called when a transaction is updated, which is a later step.
 */
@RequiredArgsConstructor
public class MovementService {

    private final MovementRepository repository;
    private final Clock clock;

    /**
     * Validates the data (the {@link Movement} constructor enforces the domain rules), assigns an id and the creation
     * time, and saves the movement.
     *
     * @throws ConflictException when the transaction already has a movement of this type, so a repeated request does
     *                           not move the money twice
     */
    public Movement record(UUID userId, UUID transactionId, BigDecimal amount, MovementType type) {
        return repository.save(new Movement(UUID.randomUUID(), userId, transactionId, amount, type, clock.instant()));
    }
}
