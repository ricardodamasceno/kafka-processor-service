package com.event.producer.application.user;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserRepository;
import com.event.producer.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.UUID;

/**
 * Application service for users. No Spring annotations; it is wired in {@code config.ServiceConfig}.
 */
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final Clock clock;

    /**
     * Validates the data (the {@link User} constructor enforces the domain rules), assigns an id and the
     * registration time, and saves the user as {@code ACTIVE}.
     *
     * @throws ConflictException when the email is already registered, regardless of case
     */
    public User register(String name, String email) {
        User user = new User(UUID.randomUUID(), name, email, clock.instant(), UserStatus.ACTIVE);
        if (repository.existsByEmail(user.email())) {
            throw new ConflictException("A user with this email already exists");
        }
        // Two requests can pass the check above at the same time: the unique index decides, and the
        // repository reports the loser with the same exception.
        return repository.save(user);
    }
}
