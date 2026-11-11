package com.event.producer.application.user;

import com.event.producer.domain.user.UserStatus;

import java.util.UUID;

/**
 * Outbound port: a fast copy of the status of each user, kept outside the database.
 * The database is the source of truth; this copy only makes reads cheaper.
 * The Redis implementation lives in {@code adapter.cache}.
 */
public interface UserStatusCache {

    /**
     * Stores the status of a user. It is best effort: an implementation must not throw when the cache is
     * unavailable, so a cache failure never fails the operation that wrote to the database.
     */
    void put(UUID userId, UserStatus status);
}
