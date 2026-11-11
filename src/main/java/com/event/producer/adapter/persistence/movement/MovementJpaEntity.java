package com.event.producer.adapter.persistence.movement;

import com.event.producer.domain.movement.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Database representation of a movement (table {@code movements}).
 * Not the domain entity: it only exists to map the table, so the domain {@code Movement} stays free of persistence annotations.
 */
@Entity
@Table(name = "movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MovementJpaEntity {

    @Id
    private UUID id;

    /** A plain id, not an association: users, transactions and movements are separate contexts. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
