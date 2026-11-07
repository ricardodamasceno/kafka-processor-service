package com.event.producer.domain.click;

import com.event.producer.domain.shared.DomainValidationException;
import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

/**
 * A click made by a user on an ad.
 * <p>
 * This is a domain entity: no runtime framework, just an immutable record. Lombok's {@code @NonNull}
 * generates the null checks at compile time (it throws {@link NullPointerException}); the rules
 * that Lombok cannot express, such as "not blank", stay in the constructor.
 */
public record Click(@NonNull UUID id, @NonNull String adId, @NonNull String userId, @NonNull Instant clickedAt) {

    public Click {
        if (adId.isBlank()) {
            throw new DomainValidationException("adId must not be blank");
        }
        if (userId.isBlank()) {
            throw new DomainValidationException("userId must not be blank");
        }
    }
}
