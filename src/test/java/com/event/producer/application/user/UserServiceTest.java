package com.event.producer.application.user;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private final InMemoryUserRepository repository = new InMemoryUserRepository();
    private final UserService service = new UserService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registerSavesTheUserWithAnIdAndTheTimeFromTheClock() {
        User user = service.register("Alice Silva", "alice@example.com");

        assertEquals(NOW, user.createdAt());
        assertEquals(user, repository.store.get(user.id()));
    }

    @Test
    void aNewUserIsActive() {
        User user = service.register("Alice", "alice@example.com");

        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(UserStatus.ACTIVE, repository.store.get(user.id()).status());
    }

    @Test
    void registerStoresTheEmailNormalized() {
        User user = service.register("  Alice  ", "  Alice@Example.COM ");

        assertEquals("Alice", user.name());
        assertEquals("alice@example.com", user.email());
    }

    @Test
    void eachRegistrationGetsItsOwnId() {
        User first = service.register("Alice", "alice@example.com");
        User second = service.register("Bob", "bob@example.com");

        assertNotEquals(first.id(), second.id());
        assertEquals(2, repository.store.size());
    }

    @Test
    void aSecondRegistrationWithTheSameEmailIsRejectedRegardlessOfCase() {
        service.register("Alice", "alice@example.com");

        assertThrows(ConflictException.class, () -> service.register("Another Alice", "ALICE@example.com"));
        assertEquals(1, repository.store.size());
    }

    @Test
    void invalidDataIsRejectedAndNothingIsStored() {
        assertThrows(DomainValidationException.class, () -> service.register(" ", "alice@example.com"));
        assertThrows(DomainValidationException.class, () -> service.register("Alice", "not-an-email"));

        assertEquals(0, repository.store.size());
    }
}
