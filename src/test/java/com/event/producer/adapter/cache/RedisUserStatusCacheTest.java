package com.event.producer.adapter.cache;

import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks what is written to Redis and how a failure is handled, without a Redis server. */
class RedisUserStatusCacheTest {

    private static final UUID USER_ID = UUID.fromString("a1111111-0000-4000-8000-000000000001");
    private static final CacheProperties PROPERTIES = new CacheProperties(Duration.ofHours(1));

    /** One call to {@code ValueOperations.set(key, value, timeout)}. */
    private record Write(String key, String value, Duration ttl) {
    }

    /** A template whose value operations record what is written, instead of talking to Redis. */
    @SuppressWarnings("unchecked")
    private static StringRedisTemplate templateRecordingInto(List<Write> writes) {
        ValueOperations<String, String> operations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                RedisUserStatusCacheTest.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("set") && args.length == 3) {
                        writes.add(new Write((String) args[0], (String) args[1], (Duration) args[2]));
                    }
                    return null;
                });
        return new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return operations;
            }
        };
    }

    @Test
    void writesTheStatusUnderTheKeyOfTheUserWithTheConfiguredTtl() {
        List<Write> writes = new ArrayList<>();
        RedisUserStatusCache cache = new RedisUserStatusCache(templateRecordingInto(writes), PROPERTIES);

        cache.put(USER_ID, UserStatus.BLOCKED);

        assertEquals(List.of(new Write("user:status:" + USER_ID, "BLOCKED", Duration.ofHours(1))), writes);
    }

    @Test
    void aFailureToReachRedisIsSwallowedBecauseTheDatabaseAlreadyHoldsTheValue() {
        StringRedisTemplate redisDown = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                throw new RedisConnectionFailureException("Redis is down");
            }
        };

        assertDoesNotThrow(() -> new RedisUserStatusCache(redisDown, PROPERTIES).put(USER_ID, UserStatus.ACTIVE));
    }

    @Test
    void theTtlMustBeGreaterThanZero() {
        assertThrows(IllegalArgumentException.class, () -> new CacheProperties(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new CacheProperties(Duration.ofSeconds(-1)));
        assertDoesNotThrow(() -> new CacheProperties(Duration.ofSeconds(1)));
    }
}
