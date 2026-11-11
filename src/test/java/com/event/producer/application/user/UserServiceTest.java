package com.event.producer.application.user;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.application.shared.NotFoundException;
import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private final InMemoryUserRepository repository = new InMemoryUserRepository();
    private final RecordingUserStatusCache cache = new RecordingUserStatusCache();
    private final UserService service = new UserService(repository, Clock.fixed(NOW, ZoneOffset.UTC), cache);

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
    void registerCachesTheStatusOfTheNewUser() {
        User user = service.register("Alice", "alice@example.com");

        assertEquals(UserStatus.ACTIVE, cache.entries.get(user.id()));
        assertEquals(1, cache.writes);
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
    void aRejectedRegistrationCachesNothing() {
        service.register("Alice", "alice@example.com");

        assertThrows(ConflictException.class, () -> service.register("Another Alice", "alice@example.com"));
        assertThrows(DomainValidationException.class, () -> service.register(" ", "bob@example.com"));

        assertEquals(1, cache.writes);
        assertEquals(1, cache.entries.size());
    }

    @Test
    void invalidDataIsRejectedAndNothingIsStored() {
        assertThrows(DomainValidationException.class, () -> service.register(" ", "alice@example.com"));
        assertThrows(DomainValidationException.class, () -> service.register("Alice", "not-an-email"));

        assertEquals(0, repository.store.size());
    }

    @Test
    void updateStatusAcceptsEachOfTheThreeStatusesFromAnyStatus() {
        User user = service.register("Alice", "alice@example.com");

        for (UserStatus status : new UserStatus[]{UserStatus.BLOCKED, UserStatus.INACTIVE, UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.BLOCKED}) {
            User updated = service.updateStatus(user.id(), status);

            assertEquals(status, updated.status());
            assertEquals(status, repository.store.get(user.id()).status());
            assertEquals(status, cache.entries.get(user.id()));
        }
    }

    @Test
    void updateStatusChangesOnlyTheStatus() {
        User user = service.register("Alice Silva", "alice@example.com");

        User updated = service.updateStatus(user.id(), UserStatus.BLOCKED);

        assertEquals(user.withStatus(UserStatus.BLOCKED), updated);
        assertEquals(user.createdAt(), updated.createdAt());
    }

    @Test
    void settingTheStatusTheUserAlreadyHasIsAcceptedAndRefreshesTheCache() {
        User user = service.register("Alice", "alice@example.com");
        int writesBefore = cache.writes;

        User updated = service.updateStatus(user.id(), UserStatus.ACTIVE);

        assertEquals(UserStatus.ACTIVE, updated.status());
        assertEquals(writesBefore + 1, cache.writes);
    }

    @Test
    void updateStatusOfAnUnknownUserIsNotFoundAndCachesNothing() {
        UUID unknown = UUID.randomUUID();

        assertThrows(NotFoundException.class, () -> service.updateStatus(unknown, UserStatus.BLOCKED));

        assertEquals(0, cache.writes);
        assertEquals(0, repository.store.size());
    }
}
