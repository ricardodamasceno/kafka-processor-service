package com.event.producer.application.user;

import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Test double that implements the domain port with a map. Because services depend on the
 * {@link UserRepository} interface, they can be tested without a database.
 * Public so the tests of other services that need a user store can use it too.
 */
public class InMemoryUserRepository implements UserRepository {

    final Map<UUID, User> store = new LinkedHashMap<>();

    /** How many times a user was read by id, so a test can tell whether the database was used. */
    public int findByIdCalls;

    @Override
    public User save(User user) {
        store.put(user.id(), user);
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        findByIdCalls++;
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsByEmail(String email) {
        return store.values().stream().anyMatch(user -> user.email().equalsIgnoreCase(email));
    }
}
