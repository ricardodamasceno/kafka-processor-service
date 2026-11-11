package com.event.producer.adapter.persistence.movement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data interface; an implementation detail of this adapter. */
interface MovementJpaRepository extends JpaRepository<MovementJpaEntity, UUID> {
}
