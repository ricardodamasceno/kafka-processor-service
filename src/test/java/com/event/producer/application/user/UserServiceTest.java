package com.event.producer.application.user;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.application.shared.NotFoundException;
import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserRepository;
import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Mock
    private UserRepository repository;

    /** The cache stays a recording test double: the tests look at what it holds and how often it was written. */
    private final RecordingUserStatusCache cache = new RecordingUserStatusCache();

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, Clock.fixed(NOW, ZoneOffset.UTC), cache);
    }

    private void saveReturnsItsArgument() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private User existingUser(UserStatus status) {
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", NOW, status);
        when(repository.findById(user.id())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void registerSavesTheUserWithAnIdAndTheTimeFromTheClock() {
        saveReturnsItsArgument();

        User user = service.register("Alice Silva", "alice@example.com");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertEquals(user, saved.getValue());
        assertEquals(NOW, user.createdAt());
    }

    @Test
    void aNewUserIsActive() {
        saveReturnsItsArgument();

        assertEquals(UserStatus.ACTIVE, service.register("Alice", "alice@example.com").status());
    }

    @Test
    void registerCachesTheStatusOfTheNewUser() {
        saveReturnsItsArgument();

        User user = service.register("Alice", "alice@example.com");

        assertEquals(UserStatus.ACTIVE, cache.entries.get(user.id()));
        assertEquals(1, cache.writes);
    }

    @Test
    void registerStoresTheEmailNormalized() {
        saveReturnsItsArgument();

        User user = service.register("  Alice  ", "  Alice@Example.COM ");

        assertEquals("Alice", user.name());
        assertEquals("alice@example.com", user.email());
    }

    @Test
    void eachRegistrationGetsItsOwnId() {
        saveReturnsItsArgument();

        User first = service.register("Alice", "alice@example.com");
        User second = service.register("Bob", "bob@example.com");

        assertNotEquals(first.id(), second.id());
        verify(repository, times(2)).save(any());
    }

    @Test
    void anEmailThatIsAlreadyRegisteredIsRejectedRegardlessOfCase() {
        // The email is normalized before the check, so a different case reaches the repository in lower case
        when(repository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.register("Another Alice", "ALICE@example.com"));

        verify(repository, never()).save(any());
    }

    @Test
    void aConflictFromTheUniqueIndexIsNotHiddenAndCachesNothing() {
        // Two requests can pass the check at the same time: the repository reports the loser
        when(repository.save(any())).thenThrow(new ConflictException("A user with this email already exists"));

        assertThrows(ConflictException.class, () -> service.register("Alice", "alice@example.com"));

        assertEquals(0, cache.writes);
    }

    @Test
    void aRejectedRegistrationCachesNothing() {
        when(repository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.register("Alice", "alice@example.com"));
        assertThrows(DomainValidationException.class, () -> service.register(" ", "bob@example.com"));

        assertEquals(0, cache.writes);
        assertEquals(0, cache.entries.size());
    }

    @Test
    void invalidDataIsRejectedAndNothingIsStored() {
        assertThrows(DomainValidationException.class, () -> service.register(" ", "alice@example.com"));
        assertThrows(DomainValidationException.class, () -> service.register("Alice", "not-an-email"));

        verifyNoInteractions(repository);
    }

    @Test
    void updateStatusAcceptsEachOfTheThreeStatusesFromAnyStatus() {
        User user = existingUser(UserStatus.ACTIVE);
        saveReturnsItsArgument();

        for (UserStatus status : new UserStatus[]{UserStatus.BLOCKED, UserStatus.INACTIVE, UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.BLOCKED}) {
            User updated = service.updateStatus(user.id(), status);

            assertEquals(status, updated.status());
            assertEquals(status, cache.entries.get(user.id()));
        }
    }

    @Test
    void updateStatusChangesOnlyTheStatus() {
        User user = existingUser(UserStatus.ACTIVE);
        saveReturnsItsArgument();

        User updated = service.updateStatus(user.id(), UserStatus.BLOCKED);

        assertEquals(user.withStatus(UserStatus.BLOCKED), updated);
        verify(repository).save(user.withStatus(UserStatus.BLOCKED));
    }

    @Test
    void settingTheStatusTheUserAlreadyHasIsAcceptedAndRefreshesTheCache() {
        User user = existingUser(UserStatus.ACTIVE);
        saveReturnsItsArgument();

        User updated = service.updateStatus(user.id(), UserStatus.ACTIVE);

        assertEquals(UserStatus.ACTIVE, updated.status());
        assertEquals(1, cache.writes);
    }

    @Test
    void updateStatusOfAnUnknownUserIsNotFoundAndCachesNothing() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.updateStatus(unknown, UserStatus.BLOCKED));

        assertEquals(0, cache.writes);
        verify(repository, never()).save(any());
    }

    @Test
    void statusOfReadsTheCacheFirstAndDoesNotTouchTheDatabase() {
        UUID userId = UUID.randomUUID();
        cache.entries.put(userId, UserStatus.BLOCKED); // whatever the database says, the cache wins

        assertEquals(Optional.of(UserStatus.BLOCKED), service.statusOf(userId));

        verifyNoInteractions(repository);
    }

    @Test
    void statusOfFallsBackToTheDatabaseWhenTheCacheHasNoEntryAndFillsTheCache() {
        User user = existingUser(UserStatus.INACTIVE);

        assertEquals(Optional.of(UserStatus.INACTIVE), service.statusOf(user.id()));

        assertEquals(UserStatus.INACTIVE, cache.entries.get(user.id()));
        assertEquals(1, cache.writes);
    }

    @Test
    void statusOfAnUnknownUserIsEmptyAndFillsNothing() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), service.statusOf(unknown));

        assertEquals(0, cache.writes);
    }
}
