package com.event.producer.application.shared;

/**
 * Thrown when an event could not be published to the message broker (for example the broker is
 * unreachable or did not acknowledge in time). The web layer translates it to an HTTP 503.
 */
public class PublishFailedException extends RuntimeException {

    public PublishFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
