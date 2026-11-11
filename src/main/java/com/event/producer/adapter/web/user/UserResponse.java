package com.event.producer.adapter.web.user;

import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

/** JSON body returned by the API for a user. */
public record UserResponse(UUID id, String name, String email, Instant createdAt, UserStatus status) {

    static UserResponse from(User user) {
        return new UserResponse(user.id(), user.name(), user.email(), user.createdAt(), user.status());
    }
}
