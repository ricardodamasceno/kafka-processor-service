package com.event.producer.application.movement;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.domain.movement.Movement;
import com.event.producer.domain.movement.MovementRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double that implements the domain port with a list. Like the unique index of the database, it rejects a second
 * movement of the same type for a transaction.
 */
class InMemoryMovementRepository implements MovementRepository {

    final List<Movement> store = new ArrayList<>();

    @Override
    public Movement save(Movement movement) {
        boolean duplicate = store.stream().anyMatch(existing ->
                existing.transactionId().equals(movement.transactionId()) && existing.type() == movement.type());
        if (duplicate) {
            throw new ConflictException("Transaction " + movement.transactionId() + " already has a "
                    + movement.type() + " movement");
        }
        store.add(movement);
        return movement;
    }
}
