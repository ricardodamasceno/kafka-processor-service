package com.event.producer.application.transaction;

/**
 * Outbound port: how the application announces what happened to a transaction.
 * The Kafka implementation lives in {@code adapter.messaging.transaction}.
 */
public interface TransactionEventPublisher {

    /**
     * Announces the event. Returns only after the broker has acknowledged it; throws
     * {@link com.event.producer.application.shared.PublishFailedException} otherwise.
     */
    void publish(TransactionEvent event);
}
