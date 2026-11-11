package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.InvalidMessageException;
import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.movement.MovementService;
import com.event.producer.application.shared.ConflictException;
import com.event.producer.application.transaction.TransactionEventType;
import com.event.producer.application.transaction.TransactionService;
import com.event.producer.domain.movement.MovementType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the events of {@code payments-transactions-update}: what happened to a transaction after it was validated.
 * When a transaction reaches {@code PROCESSING}, which is when it is carried out, the money moves, so this consumer
 * records the movement with {@link MovementService#record}: a debit for a withdrawal, a credit for anything else
 * ({@link MovementType#of}). Once the movement is saved, the transaction is completed with
 * {@link TransactionService#complete}. The other events ({@code COMPLETED} and {@code FAILED}) are not its concern and
 * are ignored.
 * <p>
 * Delivery is at-least-once, and the two writes are not atomic. A transaction has at most one movement of each type, so
 * a repeated event finds the movement already there ({@link ConflictException}); that still counts as saved, and the
 * transaction is completed anyway. This is what repairs a failure between the two writes: the retry finds the movement,
 * and completes the transaction. Completing only moves a transaction that is still {@code PROCESSING}, so doing it
 * twice changes nothing. Any other failure (the database is down, for instance) is retried and then the message goes to
 * the dead letter queue. A message that cannot be understood goes there without retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TransactionUpdatedConsumer {

    private final JsonMessageCodec codec;
    private final MovementService movementService;
    private final TransactionService transactionService;

    @KafkaListener(topics = "${app.messaging.topics.transactions-update}", groupId = "movement-recorder")
    void onMessage(String payload) {
        TransactionEventMessage message = codec.fromJson(payload, TransactionEventMessage.class);
        if (message.type() == null || message.transaction() == null) {
            throw new InvalidMessageException("Update event without a type or a transaction: " + message.eventId(), null);
        }
        if (message.type() != TransactionEventType.TRANSACTION_PROCESSING) {
            log.debug("Ignored an update that moves no money: {} ({})", message.type(), message.eventId());
            return;
        }

        TransactionEventMessage.TransactionData transaction = message.transaction();
        if (transaction.id() == null || transaction.userId() == null
                || transaction.type() == null || transaction.amount() == null) {
            throw new InvalidMessageException(
                    "Processing event without the id, user, type or amount of the transaction: " + message.eventId(), null);
        }

        MovementType type = MovementType.of(transaction.type());
        try {
            movementService.record(transaction.userId(), transaction.id(), transaction.amount(), type);
            log.info("Transaction {}: recorded a {} movement", transaction.id(), type);
        } catch (ConflictException e) {
            log.info("Transaction {} already has a {} movement; the event is a duplicate", transaction.id(), type);
        }

        // Reached when the movement is saved, or was already: a failure above never completes the transaction
        boolean completed = transactionService.complete(transaction.id());
        log.info("Transaction {}: {}", transaction.id(), completed ? "completed" : "was not PROCESSING, left alone");
    }
}
