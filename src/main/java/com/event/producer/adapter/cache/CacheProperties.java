package com.event.producer.adapter.cache;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Cache settings read from {@code application.yml} (prefix {@code app.cache}).
 *
 * @param userStatusTtl how long the status of a user stays in Redis. It also bounds how stale the cached status
 *                      can be when an update could not reach the cache, because the entry expires on its own
 */
@Validated
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(@NotNull Duration userStatusTtl) {

    public CacheProperties {
        if (userStatusTtl != null && (userStatusTtl.isZero() || userStatusTtl.isNegative())) {
            throw new IllegalArgumentException("app.cache.user-status-ttl must be greater than zero");
        }
    }
}
