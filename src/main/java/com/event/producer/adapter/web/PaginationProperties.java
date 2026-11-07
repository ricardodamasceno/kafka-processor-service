package com.event.producer.adapter.web;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pagination settings read from {@code application.yml} (prefix {@code app.pagination}).
 * The values live in configuration, not in the controllers, so they can change without a code change.
 */
@Validated
@ConfigurationProperties(prefix = "app.pagination")
public record PaginationProperties(@Positive int defaultPageSize, @Positive int maxPageSize) {

    /** Pages start at 0; a negative page becomes 0. */
    public int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    /** Keeps the requested size between 1 and the configured maximum. */
    public int sanitizeSize(int size) {
        return Math.min(Math.max(size, 1), maxPageSize);
    }
}
