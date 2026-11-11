package com.event.producer.adapter.persistence.movement;

import com.event.producer.domain.movement.Movement;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Converts the domain {@link Movement} to the database {@link MovementJpaEntity}. Movements are only written for now,
 * so there is no conversion back. Its only responsibility is this translation. Pure functions, so the methods are static and the
 * class is not a Spring bean.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MovementMapper {

    static MovementJpaEntity toEntity(Movement movement) {
        return new MovementJpaEntity(movement.id(), movement.userId(), movement.transactionId(), movement.amount(),
                movement.type(), movement.createdAt());
    }
}
