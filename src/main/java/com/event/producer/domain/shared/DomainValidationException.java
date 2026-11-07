package com.event.producer.domain.shared;

/**
 * Thrown when a domain rule (invariant) is violated, for example a blank id or a non-positive amount.
 * It is a plain Java exception: the domain layer knows nothing about HTTP or Spring.
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
