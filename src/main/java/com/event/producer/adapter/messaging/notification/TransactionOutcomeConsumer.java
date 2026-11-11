package com.event.producer.adapter.messaging.notification;

import com.event.producer.adapter.messaging.InvalidMessageException;
import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.notification.NotificationService;
import com.event.producer.domain.notification.TransactionOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The entry point of the notification context. Reads {@code payments-transactions-update} with its own consumer group,
 * so it receives every event independently of the movement recorder, and tells the user when a transaction
 * {@code COMPLETED} or {@code FAILED}. The other events are ignored.
 * <p>
 * Delivery is at-least-once, so a user can be told twice about the same event; a real notifier would use the event id
 * to avoid that. A failure is retried and then the message goes to the dead letter queue of the topic. A message that
 * cannot be understood goes there without retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TransactionOutcomeConsumer {

    private final JsonMessageCodec codec;
    private final NotificationService notificationService;

    @KafkaListener(topics = "${app.messaging.topics.transactions-update}", groupId = "transaction-notifier")
    void onMessage(String payload) {
        TransactionOutcomeMessage message = codec.fromJson(payload, TransactionOutcomeMessage.class);
        if (message.type() == null || message.transaction() == null) {
            throw new InvalidMessageException("Update event without a type or a transaction: " + message.eventId(), null);
        }

        TransactionOutcome outcome = switch (message.type()) {
            case "TRANSACTION_COMPLETED" -> TransactionOutcome.COMPLETED;
            case "TRANSACTION_FAILED" -> TransactionOutcome.FAILED;
            default -> null;
        };
        if (outcome == null) {
            log.debug("Ignored an event that is not an outcome: {} ({})", message.type(), message.eventId());
            return;
        }
        if (message.transaction().id() == null || message.transaction().userId() == null) {
            throw new InvalidMessageException(
                    "Outcome event without the id or the user of the transaction: " + message.eventId(), null);
        }

        notificationService.notifyOutcome(message.transaction().userId(), message.transaction().id(), outcome);
    }
}
