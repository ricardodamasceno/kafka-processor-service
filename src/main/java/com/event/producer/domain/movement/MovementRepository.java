package com.event.producer.domain.movement;

/**
 * Port (interface) that describes what the application needs from a movement store.
 * The implementation lives in {@code adapter.persistence.movement}.
 */
public interface MovementRepository {

    /**
     * Saves a new movement.
     *
     * @throws com.event.producer.application.shared.ConflictException when the transaction already has a movement of
     *                                                                that type
     */
    Movement save(Movement movement);
}
