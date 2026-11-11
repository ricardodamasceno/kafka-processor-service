package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.InvalidMessageException;
import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.transaction.TransactionEventType;
import com.event.producer.application.transaction.TransactionService;
import com.event.producer.application.transaction.ValidationOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the {@code TRANSACTION_CREATED} events from {@code payments-transactions-create}: the work to do for a
 * transaction that was just created and is {@code PENDING}. Its job is the validation step, done by
 * {@link TransactionService#validate}; the transaction ends the step as {@code PROCESSING} or {@code FAILED}.
 * <p>
 * Delivery is at-least-once, and the validation only moves a transaction that is still {@code PENDING}, so an event
 * delivered twice changes nothing the second time. A failure (the database is down, for instance) is retried and
 * then the message goes to the dead letter queue, with the transaction still {@code PENDING}. A message that cannot
 * be understood goes there without retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TransactionCreatedConsumer {

    private final JsonMessageCodec codec;
    private final TransactionService transactionService;

    @KafkaListener(topics = "${app.messaging.topics.transactions-create}", groupId = "transaction-validator")
    void onMessage(String payload) {
        TransactionEventMessage message = codec.fromJson(payload, TransactionEventMessage.class);
        if (message.type() == null || message.transaction() == null
                || message.transaction().id() == null || message.transaction().userId() == null) {
            throw new InvalidMessageException(
                    "Creation event without a type, a transaction or its user: " + message.eventId(), null);
        }
        if (message.type() != TransactionEventType.TRANSACTION_CREATED) {
            log.warn("Ignored an event that is not a creation: {} ({})", message.type(), message.eventId());
            return;
        }

        ValidationOutcome outcome = transactionService.validate(message.transaction().id(), message.transaction().userId());
        log.info("Transaction {} validated: {}", message.transaction().id(), outcome);
    }
}
