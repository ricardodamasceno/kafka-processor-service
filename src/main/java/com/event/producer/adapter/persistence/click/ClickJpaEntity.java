package com.event.producer.adapter.persistence.click;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Database representation of a click (table {@code clicks}).
 * <p>
 * This is NOT the domain entity. It carries JPA annotations and a mutable shape that Hibernate
 * needs, which would pollute the domain if they were mixed. The adapter converts between the two.
 * <p>
 * Lombok generates the getters and constructors: JPA requires a no-arg constructor
 * (kept protected so application code cannot use it), and the all-args one is used by the adapter.
 */
@Entity
@Table(name = "clicks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ClickJpaEntity {

    @Id
    private UUID id;

    @Column(name = "ad_id", nullable = false)
    private String adId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;
}
