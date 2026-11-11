package com.event.producer.adapter.messaging.notification;

import java.util.UUID;

/**
 * What the notification context reads from a transaction event. It declares only the fields it needs and ignores the
 * rest, so it does not depend on the classes of the transaction context: the JSON on the topic is the contract between
 * them. The type is text, so a type this context does not know is not an error.
 */
record TransactionOutcomeMessage(String eventId, String type, TransactionData transaction) {

    record TransactionData(UUID id, UUID userId) {
    }
}
