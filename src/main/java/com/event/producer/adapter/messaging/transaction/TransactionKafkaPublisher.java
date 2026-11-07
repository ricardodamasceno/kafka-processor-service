package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.KafkaMessageSender;
import com.event.producer.adapter.messaging.MessagingProperties;
import com.event.producer.application.transaction.TransactionEventPublisher;
import com.event.producer.domain.transaction.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Kafka implementation of the {@link TransactionEventPublisher} port (the producer).
 * <p>
 * The message key is the transaction id, so all events about one transaction share a partition.
 * If a business rule later needs ordering per account (for example a balance check), the key
 * should become the account id instead.
 */
@Component
@RequiredArgsConstructor
class TransactionKafkaPublisher implements TransactionEventPublisher {

    private final KafkaMessageSender sender;
    private final MessagingProperties properties;

    @Override
    public void publishCreationRequested(Transaction transaction) {
        sender.send(properties.topics().transactions(), transaction.id().toString(),
                TransactionMessageMapper.toMessage(transaction));
    }
}
