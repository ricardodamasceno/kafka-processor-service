package com.event.producer.adapter.persistence.click;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data interface: Spring generates the implementation (save, findById, paging...).
 * It is an implementation detail of this adapter and is never used outside this package.
 */
interface ClickJpaRepository extends JpaRepository<ClickJpaEntity, UUID> {
}
