package com.event.producer.application.shared;

/** Thrown when an event could not be published (for example the broker did not acknowledge it in time). */
public class PublishFailedException extends RuntimeException {

    public PublishFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
