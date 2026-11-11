package com.event.producer.adapter.messaging.cdc;

import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A change of the {@code transactions} table, as Debezium publishes it to {@code transactions-cdc-events}.
 * <p>
 * Only the fields this service reads are mapped; the rest of the envelope (source, timestamps) is ignored.
 * The row fields keep the column names of the table, so the table schema is the contract of the topic.
 * Nothing outside this package should know this format: the translator turns it into transaction events.
 *
 * @param op     the operation: {@code c} (insert), {@code u} (update) or {@code d} (delete)
 * @param before the row before the change: {@code null} for an insert
 * @param after  the row after the change: {@code null} for a delete
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionChangeMessage(String op, Row before, Row after) {

    /** The column values of one row. {@code amount} travels as text to keep its exact scale. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("account_id") String accountId,
            TransactionType type,
            BigDecimal amount,
            @JsonProperty("occurred_at") Instant occurredAt,
            TransactionStatus status) {
    }

    /** The row the change is about: the new one, or the deleted one for a delete. */
    public Row current() {
        return after != null ? after : before;
    }
}
