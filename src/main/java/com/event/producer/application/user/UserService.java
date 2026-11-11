package com.event.producer.application.user;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.application.shared.NotFoundException;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserRepository;
import com.event.producer.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for users. No Spring annotations; it is wired in {@code config.ServiceConfig}.
 * <p>
 * Every time a user is saved, the status is also written to the {@link UserStatusCache}, after the database,
 * which stays the source of truth.
 */
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final Clock clock;
    private final UserStatusCache statusCache;

    /**
     * Validates the data (the {@link User} constructor enforces the domain rules), assigns an id and the
     * registration time, saves the user as {@code ACTIVE} and caches the status.
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
        User saved = repository.save(user);
        statusCache.put(saved.id(), saved.status());
        return saved;
    }

    /**
     * The status of a user: from the cache when it is there, otherwise from the database, filling the cache for the
     * next time. Empty when the user does not exist.
     * <p>
     * The cache can be missing or briefly different from the database (see ADR 0007), and an entry found there is
     * trusted. The cache is filled only after a database read, so it never holds a status that was not read from
     * the database or written by {@link #register} or {@link #updateStatus}.
     */
    public Optional<UserStatus> statusOf(UUID userId) {
        Optional<UserStatus> cached = statusCache.find(userId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<User> user = repository.findById(userId);
        user.ifPresent(found -> statusCache.put(found.id(), found.status()));
        return user.map(User::status);
    }

    /**
     * Sets the status of a user and refreshes the cached status. Any status can be set from any other
     * ({@code ACTIVE}, {@code INACTIVE} or {@code BLOCKED}). Setting the status the user already has is accepted:
     * it changes nothing in the database and refreshes the cache.
     *
     * @throws NotFoundException when the user does not exist
     */
    public User updateStatus(UUID id, UserStatus status) {
        User user = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
        User updated = repository.save(user.withStatus(status));
        statusCache.put(updated.id(), updated.status());
        return updated;
    }
}
