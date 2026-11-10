package com.event.producer.adapter.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Messaging settings read from {@code application.yml} (prefix {@code app.messaging}):
 * topic names, how long to wait for the broker acknowledgement, and the consumer retry policy.
 */
@Validated
@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        @NotNull Duration sendTimeout,
        @NotNull @Valid Retry retry,
        @NotNull @Valid Topics topics) {

    /** How many times a failed message is retried (after the first attempt) and the wait between retries. */
    public record Retry(@PositiveOrZero long maxRetries, @NotNull Duration interval) {
    }

    /** Topic names. Each source topic has a dead letter queue (DLQ) for messages that cannot be processed. */
    public record Topics(
            @NotBlank String transactions,
            @NotBlank String transactionsDlq) {
    }
}
