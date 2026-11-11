package com.event.producer.adapter.cache;

import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks what is written to and read from Redis and how a failure is handled, without a Redis server. */
class RedisUserStatusCacheTest {

    private static final UUID USER_ID = UUID.fromString("a1111111-0000-4000-8000-000000000001");
    private static final String KEY = "user:status:" + USER_ID;
    private static final CacheProperties PROPERTIES = new CacheProperties(Duration.ofMinutes(1));

    /** One call to {@code ValueOperations.set(key, value, timeout)}. */
    private record Write(String key, String value, Duration ttl) {
    }

    /**
     * A template whose value operations work on a map instead of talking to Redis: {@code set} records the call and
     * stores the value, and {@code get} reads from the map.
     */
    @SuppressWarnings("unchecked")
    private static StringRedisTemplate templateOver(Map<String, String> data, List<Write> writes) {
        ValueOperations<String, String> operations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                RedisUserStatusCacheTest.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("set") && args.length == 3) {
                        writes.add(new Write((String) args[0], (String) args[1], (Duration) args[2]));
                        data.put((String) args[0], (String) args[1]);
                    }
                    if (method.getName().equals("get") && args.length == 1) {
                        return data.get((String) args[0]);
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

    private static StringRedisTemplate redisDown() {
        return new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                throw new RedisConnectionFailureException("Redis is down");
            }
        };
    }

    @Test
    void writesTheStatusUnderTheKeyOfTheUserWithTheConfiguredTtl() {
        List<Write> writes = new ArrayList<>();
        RedisUserStatusCache cache = new RedisUserStatusCache(templateOver(new HashMap<>(), writes), PROPERTIES);

        cache.put(USER_ID, UserStatus.BLOCKED);

        assertEquals(List.of(new Write(KEY, "BLOCKED", Duration.ofMinutes(1))), writes);
    }

    @Test
    void aFailureToWriteIsSwallowedBecauseTheDatabaseAlreadyHoldsTheValue() {
        assertDoesNotThrow(() -> new RedisUserStatusCache(redisDown(), PROPERTIES).put(USER_ID, UserStatus.ACTIVE));
    }

    @Test
    void findReturnsTheCachedStatus() {
        Map<String, String> data = new HashMap<>(Map.of(KEY, "BLOCKED"));
        RedisUserStatusCache cache = new RedisUserStatusCache(templateOver(data, new ArrayList<>()), PROPERTIES);

        assertEquals(Optional.of(UserStatus.BLOCKED), cache.find(USER_ID));
    }

    @Test
    void whatWasWrittenCanBeReadBack() {
        RedisUserStatusCache cache = new RedisUserStatusCache(templateOver(new HashMap<>(), new ArrayList<>()), PROPERTIES);

        cache.put(USER_ID, UserStatus.INACTIVE);

        assertEquals(Optional.of(UserStatus.INACTIVE), cache.find(USER_ID));
    }

    @Test
    void findIsEmptyWhenThereIsNoEntry() {
        RedisUserStatusCache cache = new RedisUserStatusCache(templateOver(new HashMap<>(), new ArrayList<>()), PROPERTIES);

        assertEquals(Optional.empty(), cache.find(USER_ID));
    }

    @Test
    void findIsEmptyWhenTheEntryIsNotAStatusSoTheCallerReadsTheDatabase() {
        Map<String, String> data = new HashMap<>(Map.of(KEY, "not-a-status"));
        RedisUserStatusCache cache = new RedisUserStatusCache(templateOver(data, new ArrayList<>()), PROPERTIES);

        assertEquals(Optional.empty(), cache.find(USER_ID));
    }

    @Test
    void findIsEmptyAndDoesNotThrowWhenRedisIsDown() {
        assertEquals(Optional.empty(), new RedisUserStatusCache(redisDown(), PROPERTIES).find(USER_ID));
    }

    @Test
    void theTtlMustBeGreaterThanZero() {
        assertThrows(IllegalArgumentException.class, () -> new CacheProperties(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new CacheProperties(Duration.ofSeconds(-1)));
        assertDoesNotThrow(() -> new CacheProperties(Duration.ofSeconds(1)));
    }
}
