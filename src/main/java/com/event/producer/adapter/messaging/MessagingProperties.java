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

    /**
     * Topic names. The change events are read from {@code transactionsCdcEvents}, and a message that cannot be
     * processed goes to its dead letter queue (DLQ). The translated events are published to
     * {@code transactionsCreate} and {@code transactionsUpdate}; the creation events are also read from
     * {@code transactionsCreate}, with {@code transactionsCreateDlq} as its dead letter queue. The update events are
     * read from {@code transactionsUpdate}, with {@code transactionsUpdateDlq} as its dead letter queue.
     */
    public record Topics(
            @NotBlank String transactionsCdcEvents,
            @NotBlank String transactionsCdcEventsDlq,
            @NotBlank String transactionsCreate,
            @NotBlank String transactionsCreateDlq,
            @NotBlank String transactionsUpdate,
            @NotBlank String transactionsUpdateDlq) {
    }
}
