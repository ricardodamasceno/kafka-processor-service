package com.event.producer.adapter.persistence.user;

import com.event.producer.domain.user.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Converts between the domain {@link User} and the database {@link UserJpaEntity}.
 * Its only responsibility is this translation. Pure functions, so the methods are static and the
 * class is not a Spring bean.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UserMapper {

    static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(user.id(), user.name(), user.email(), user.createdAt(), user.status());
    }

    static User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getName(), entity.getEmail(), entity.getCreatedAt(), entity.getStatus());
    }
}
