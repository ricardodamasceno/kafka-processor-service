package com.event.producer.adapter.cache;

import com.event.producer.application.user.UserStatusCache;
import com.event.producer.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Redis implementation of the {@link UserStatusCache} port.
 * <p>
 * One entry per user: the key is {@code user:status:<userId>} and the value is the status name. The entry expires
 * after {@code app.cache.user-status-ttl}. A failure to reach Redis is logged and swallowed, as the port requires:
 * the database already holds the right value.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RedisUserStatusCache implements UserStatusCache {

    static final String KEY_PREFIX = "user:status:";

    private final StringRedisTemplate redis;
    private final CacheProperties properties;

    @Override
    public void put(UUID userId, UserStatus status) {
        try {
            redis.opsForValue().set(KEY_PREFIX + userId, status.name(), properties.userStatusTtl());
        } catch (RuntimeException e) {
            log.warn("Could not cache the status of user {}: {}", userId, e.toString());
        }
    }
}
