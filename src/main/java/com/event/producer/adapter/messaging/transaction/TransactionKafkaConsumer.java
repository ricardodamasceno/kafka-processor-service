package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.transaction.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes transaction events and writes the transactions to the database.
 * Delivery is at-least-once and {@code TransactionService.persist} is idempotent: see {@code ClickKafkaConsumer}.
 */
@Component
@RequiredArgsConstructor
class TransactionKafkaConsumer {

    private final TransactionService service;
    private final JsonMessageCodec codec;

    @KafkaListener(topics = "${app.messaging.topics.transactions}", groupId = "transaction-persister")
    void onMessage(String payload) {
        TransactionMessage message = codec.fromJson(payload, TransactionMessage.class);
        service.persist(TransactionMessageMapper.toDomain(message));
    }
}
