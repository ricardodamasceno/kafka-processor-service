package com.event.producer.application.shared;

/**
 * Thrown when a request conflicts with what already exists, for example an email that is already registered.
 * The web layer translates it to an HTTP 409; the service does not know that.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
