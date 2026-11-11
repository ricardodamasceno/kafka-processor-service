package com.event.producer.adapter.persistence.movement;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.domain.movement.Movement;
import com.event.producer.domain.movement.MovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements the domain port {@link MovementRepository} using Spring Data JPA.
 * The conversion between domain and database objects is delegated to {@link MovementMapper}.
 */
@Repository
@RequiredArgsConstructor
class MovementRepositoryAdapter implements MovementRepository {

    /** The unique index on {@code (transaction_id, type)}, created by migration V5. */
    private static final String TRANSACTION_TYPE_INDEX = "uq_movements_transaction_id_type";

    private final MovementJpaRepository jpaRepository;

    @Override
    public Movement save(Movement movement) {
        try {
            jpaRepository.save(MovementMapper.toEntity(movement));
            return movement;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) {
                throw new ConflictException(
                        "Transaction " + movement.transactionId() + " already has a " + movement.type() + " movement", e);
            }
            throw e;
        }
    }

    private static boolean isDuplicate(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.contains(TRANSACTION_TYPE_INDEX);
    }
}
