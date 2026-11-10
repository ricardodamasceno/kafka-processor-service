package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.KafkaMessageSender;
import com.event.producer.adapter.messaging.MessagingProperties;
import com.event.producer.application.transaction.TransactionEvent;
import com.event.producer.application.transaction.TransactionEventPublisher;
import com.event.producer.application.transaction.TransactionEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Kafka implementation of the {@link TransactionEventPublisher} port. The message key is the transaction id,
 * so all the events of one transaction go to the same partition and keep their order.
 */
@Component
@RequiredArgsConstructor
class TransactionEventKafkaPublisher implements TransactionEventPublisher {

    private final KafkaMessageSender sender;
    private final MessagingProperties properties;

    @Override
    public void publish(TransactionEvent event) {
        sender.send(topicFor(event.type()), event.transaction().id().toString(), TransactionEventMessage.from(event));
    }

    /** The creation is work to do (the executor reads it); the other events are facts about what happened. */
    private String topicFor(TransactionEventType type) {
        MessagingProperties.Topics topics = properties.topics();
        return type == TransactionEventType.TRANSACTION_CREATED ? topics.transactionsCreate() : topics.transactionsUpdate();
    }
}
