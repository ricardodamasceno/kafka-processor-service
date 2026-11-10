package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.transaction.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes transaction events and writes the transactions to the database.
 * Delivery is at-least-once (a message can arrive twice, for example after a crash before the offset is committed),
 * so {@code TransactionService.persist} is idempotent: saving the same id twice overwrites the same row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TransactionKafkaConsumer {

    private final TransactionService service;
    private final JsonMessageCodec codec;

    @KafkaListener(topics = "${app.messaging.topics.transactions}", groupId = "transaction-persister")
    void onMessage(String payload) {
        log.info("Event received to save transaction: {}", payload);
        TransactionMessage message = codec.fromJson(payload, TransactionMessage.class);
        service.persist(TransactionMessageMapper.toDomain(message));
    }
}
