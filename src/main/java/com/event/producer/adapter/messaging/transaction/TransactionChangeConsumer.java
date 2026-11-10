package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.JsonMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the changes of the {@code transactions} table from {@code transactions-cdc-events}.
 * <p>
 * For now it only logs each change. It is the place where a reaction will go, for example executing a
 * transaction when a row is inserted as {@code PENDING}. Delivery is at-least-once, so a reaction must
 * tolerate seeing the same change twice. A message that cannot be read goes to the dead letter queue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TransactionChangeConsumer {

    private final JsonMessageCodec codec;

    @KafkaListener(topics = "${app.messaging.topics.transactions-cdc-events}", groupId = "transaction-change-logger")
    void onMessage(String payload) {
        TransactionChangeMessage change = codec.fromJson(payload, TransactionChangeMessage.class);
        TransactionChangeMessage.Row current = change.current();
        log.info("Transaction change: op={}, id={}, status {} -> {}",
                change.op(),
                current == null ? null : current.id(),
                change.before() == null ? null : change.before().status(),
                change.after() == null ? null : change.after().status());
    }
}
