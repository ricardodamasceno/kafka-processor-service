package com.event.producer.application.shared;

/**
 * Thrown by an application service when the requested resource does not exist.
 * The web layer translates it to an HTTP 404; the service does not know that.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
