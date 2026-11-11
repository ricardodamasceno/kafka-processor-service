package com.event.producer.application.user;

import com.event.producer.domain.user.UserStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: a fast copy of the status of each user, kept outside the database.
 * The database is the source of truth; this copy only makes reads cheaper.
 * The Redis implementation lives in {@code adapter.cache}.
 * <p>
 * Both operations are best effort: an implementation must not throw when the cache is unavailable, so a cache
 * failure never fails the operation that used it.
 */
public interface UserStatusCache {

    /** Stores the status of a user. A failure is swallowed: the database already holds the right value. */
    void put(UUID userId, UserStatus status);

    /**
     * The cached status of a user. Empty when there is no entry (never written or expired) and also when the cache
     * is unavailable, so the caller reads the database in both cases.
     */
    Optional<UserStatus> find(UUID userId);
}
