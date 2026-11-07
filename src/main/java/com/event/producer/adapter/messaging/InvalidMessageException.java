package com.event.producer.adapter.messaging;

/**
 * Thrown when a message payload cannot be understood (malformed JSON, unknown enum value...).
 * Retrying cannot fix it, so the error handler sends such messages straight to the dead letter queue.
 */
public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
