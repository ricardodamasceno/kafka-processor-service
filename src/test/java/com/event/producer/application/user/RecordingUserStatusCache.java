package com.event.producer.application.user;

import com.event.producer.domain.user.UserStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Test double for the cache port: keeps the last status of each user and counts how many times it was written. */
class RecordingUserStatusCache implements UserStatusCache {

    final Map<UUID, UserStatus> entries = new LinkedHashMap<>();
    int writes;

    @Override
    public void put(UUID userId, UserStatus status) {
        entries.put(userId, status);
        writes++;
    }
}
