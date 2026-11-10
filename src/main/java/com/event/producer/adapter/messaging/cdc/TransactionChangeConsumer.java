package com.event.producer.adapter.messaging.cdc;

import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.transaction.TransactionEvent;
import com.event.producer.application.transaction.TransactionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the changes of the {@code transactions} table from {@code transactions-cdc-events}, translates each one
 * into a transaction event and publishes it. It is the only reader of that topic: every other consumer reads the
 * transaction events.
 * <p>
 * Delivery is at-least-once, so an event can be published twice. The event id is the same each time, so a consumer
 * can ignore a duplicate. If publishing fails the offset is not committed, the message is retried and then
 * goes to the dead letter queue. A message that cannot be read goes there without retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TransactionChangeConsumer {

    private final JsonMessageCodec codec;
    private final TransactionChangeTranslator translator;
    private final TransactionEventPublisher publisher;

    @KafkaListener(topics = "${app.messaging.topics.transactions-cdc-events}", groupId = "transaction-event-translator")
    void onMessage(String payload) {
        TransactionChangeMessage change = codec.fromJson(payload, TransactionChangeMessage.class);
        TransactionChangeMessage.Row current = change.current();
        Object id = current == null ? null : current.id();

        Optional<TransactionEvent> event = translator.translate(change);
        if (event.isEmpty()) {
            log.info("Transaction change ignored: op={}, id={}", change.op(), id);
            return;
        }
        publisher.publish(event.get());
        log.info("Transaction change translated: op={}, id={}, event={}", change.op(), id, event.get().type());
    }
}
