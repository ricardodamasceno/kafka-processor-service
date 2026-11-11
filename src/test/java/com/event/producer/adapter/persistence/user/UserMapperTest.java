package com.event.producer.adapter.persistence.user;

import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserMapperTest {

    @Test
    void convertsDomainToEntityAndBack() {
        User user = new User(UUID.randomUUID(), "Alice Silva", "alice@example.com",
                Instant.parse("2026-11-10T12:00:00Z"), UserStatus.BLOCKED);

        UserJpaEntity entity = UserMapper.toEntity(user);

        assertEquals(user.id(), entity.getId());
        assertEquals(user.name(), entity.getName());
        assertEquals(user.email(), entity.getEmail());
        assertEquals(user.createdAt(), entity.getCreatedAt());
        assertEquals(UserStatus.BLOCKED, entity.getStatus());
        assertEquals(user, UserMapper.toDomain(entity));
    }
}
