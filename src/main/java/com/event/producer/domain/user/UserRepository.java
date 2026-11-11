package com.event.producer.domain.user;

/**
 * Port (interface) that describes what the application needs from a user store.
 * The implementation lives in {@code adapter.persistence.user}.
 */
public interface UserRepository {

    User save(User user);

    /** Emails are compared regardless of case. */
    boolean existsByEmail(String email);
}
