package com.event.producer.application.user;

import com.event.producer.domain.user.UserStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Test double for the cache port: keeps the status of each user and counts how many times it was written and read.
 * Public so the tests of other services that use the cache can use it too.
 */
public class RecordingUserStatusCache implements UserStatusCache {

    public final Map<UUID, UserStatus> entries = new LinkedHashMap<>();
    public int writes;
    public int reads;

    @Override
    public void put(UUID userId, UserStatus status) {
        entries.put(userId, status);
        writes++;
    }

    @Override
    public Optional<UserStatus> find(UUID userId) {
        reads++;
        return Optional.ofNullable(entries.get(userId));
    }
}
