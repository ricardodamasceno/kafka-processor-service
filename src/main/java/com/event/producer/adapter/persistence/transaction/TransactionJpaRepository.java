package com.event.producer.adapter.persistence.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data interface; an implementation detail of this adapter. */
interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID> {
}
