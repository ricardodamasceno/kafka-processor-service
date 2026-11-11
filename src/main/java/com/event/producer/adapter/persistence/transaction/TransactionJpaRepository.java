package com.event.producer.adapter.persistence.transaction;

import com.event.producer.domain.transaction.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Spring Data interface; an implementation detail of this adapter. */
interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    /**
     * A single {@code UPDATE ... WHERE status = :status}: the database decides who wins when two callers try to move
     * the same transaction, so a transaction is never moved twice. Returns the number of rows changed (0 or 1).
     */
    @Modifying
    @Transactional
    @Query("update TransactionJpaEntity t set t.status = :newStatus where t.id = :id and t.status = :status")
    int moveStatus(@Param("id") UUID id,
                   @Param("status") TransactionStatus status,
                   @Param("newStatus") TransactionStatus newStatus);
}
