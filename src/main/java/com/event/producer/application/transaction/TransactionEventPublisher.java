package com.event.producer.application.transaction;

import com.event.producer.domain.transaction.Transaction;

/**
 * Outbound port: how the application announces transaction events to the outside world.
 * The Kafka implementation lives in {@code adapter.messaging.transaction}.
 */
public interface TransactionEventPublisher {

    /**
     * Announces that the creation of a transaction was requested. Returns only after the broker has
     * acknowledged the event; throws {@link com.event.producer.application.shared.PublishFailedException}
     * otherwise.
     */
    void publishCreationRequested(Transaction transaction);
}
