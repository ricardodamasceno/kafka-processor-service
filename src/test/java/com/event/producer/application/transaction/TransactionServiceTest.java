package com.event.producer.application.transaction;

import com.event.producer.application.shared.NotFoundException;
import com.event.producer.application.user.InMemoryUserRepository;
import com.event.producer.application.user.RecordingUserStatusCache;
import com.event.producer.application.user.UserService;
import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionServiceTest {

    private static final Instant T1 = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-07T11:00:00Z");
    private static final BigDecimal TEN = new BigDecimal("10.00");

    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final RecordingUserStatusCache cache = new RecordingUserStatusCache();
    private final UserService userService = new UserService(users, Clock.fixed(T1, ZoneOffset.UTC), cache);
    private final TransactionService service = new TransactionService(repository, users, userService);

    /** A user saved in the database only: the cache has no entry for it. */
    private UUID userWith(UserStatus status) {
        User user = new User(UUID.randomUUID(), "Alice", UUID.randomUUID() + "@example.com", T1, status);
        users.save(user);
        return user.id();
    }

    private Transaction transaction(UUID userId, TransactionStatus status) {
        Transaction transaction = new Transaction(UUID.randomUUID(), userId, "acc-1", TransactionType.DEPOSIT,
                TEN, T1, status);
        repository.save(transaction);
        return transaction;
    }

    private TransactionStatus statusOf(Transaction transaction) {
        return repository.findById(transaction.id()).orElseThrow().status();
    }

    @Test
    void createStoresAPendingTransactionOwnedByTheUser() {
        UUID userId = userWith(UserStatus.ACTIVE);

        Transaction created = service.create(userId, "acc-1", TransactionType.DEPOSIT, TEN, T1);

        assertEquals(TransactionStatus.PENDING, created.status());
        assertEquals(userId, created.userId());
        assertEquals(created, service.get(created.id()));
    }

    @Test
    void createRejectsAnUnknownUserAndStoresNothing() {
        UUID unknown = UUID.randomUUID();

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> service.create(unknown, "acc-1", TransactionType.DEPOSIT, TEN, T1));

        assertTrue(exception.getMessage().contains(unknown.toString()));
        assertTrue(service.list(0, 10).isEmpty());
    }

    @Test
    void createAcceptsAUserThatIsNotActiveAndLeavesItToTheValidation() {
        // The status of the user is not checked here: the validation of the PENDING transaction does it, and fails it
        UUID inactive = userWith(UserStatus.INACTIVE);
        UUID blocked = userWith(UserStatus.BLOCKED);

        Transaction first = service.create(inactive, "acc-1", TransactionType.DEPOSIT, TEN, T1);
        Transaction second = service.create(blocked, "acc-1", TransactionType.DEPOSIT, TEN, T2);

        assertEquals(TransactionStatus.PENDING, first.status());
        assertEquals(TransactionStatus.PENDING, second.status());
        assertEquals(2, service.list(0, 10).size());
    }

    @Test
    void createRejectsInvalidDataBeforeLookingForTheUser() {
        // The data is checked first, so a bad request is a 400 even when the user does not exist
        UUID unknown = UUID.randomUUID();

        assertThrows(DomainValidationException.class,
                () -> service.create(unknown, " ", TransactionType.DEPOSIT, TEN, T1));
        assertThrows(DomainValidationException.class,
                () -> service.create(unknown, "acc-1", TransactionType.DEPOSIT, BigDecimal.ZERO, T1));
        assertTrue(service.list(0, 10).isEmpty());
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        assertThrows(NotFoundException.class, () -> service.get(UUID.randomUUID()));
    }

    @Test
    void updateReplacesTheDataAndKeepsTheOwnerAndTheStatus() {
        UUID userId = userWith(UserStatus.ACTIVE);
        Transaction created = service.create(userId, "acc-1", TransactionType.DEPOSIT, TEN, T1);
        repository.save(new Transaction(created.id(), created.userId(), created.accountId(), created.type(),
                created.amount(), created.occurredAt(), TransactionStatus.COMPLETED));

        Transaction updated = service.update(created.id(), "acc-1", TransactionType.WITHDRAWAL, new BigDecimal("25.50"), T2);

        assertEquals(TransactionType.WITHDRAWAL, updated.type());
        assertEquals(userId, updated.userId());
        assertEquals(TransactionStatus.COMPLETED, updated.status());
        assertEquals(updated, service.get(created.id()));
    }

    @Test
    void updateThrowsNotFoundForUnknownId() {
        assertThrows(NotFoundException.class,
                () -> service.update(UUID.randomUUID(), "acc-1", TransactionType.DEPOSIT, TEN, T1));
    }

    @Test
    void deleteRemovesTheTransaction() {
        Transaction created = service.create(userWith(UserStatus.ACTIVE), "acc-1", TransactionType.DEPOSIT, TEN, T1);

        service.delete(created.id());

        assertThrows(NotFoundException.class, () -> service.get(created.id()));
        assertThrows(NotFoundException.class, () -> service.delete(created.id()));
    }

    @Test
    void listReturnsMostRecentFirstAndPaginates() {
        UUID userId = userWith(UserStatus.ACTIVE);
        Transaction older = service.create(userId, "acc-1", TransactionType.DEPOSIT, TEN, T1);
        Transaction newer = service.create(userId, "acc-1", TransactionType.WITHDRAWAL, TEN, T2);

        assertEquals(List.of(newer, older), service.list(0, 10));
        assertEquals(List.of(newer), service.list(0, 1));
        assertEquals(List.of(older), service.list(1, 1));
        assertTrue(service.list(5, 10).isEmpty());
    }

    @Test
    void aTransactionOfAnActiveUserMovesToProcessing() {
        Transaction transaction = transaction(userWith(UserStatus.ACTIVE), TransactionStatus.PENDING);

        ValidationOutcome outcome = service.validate(transaction.id(), transaction.userId());

        assertEquals(ValidationOutcome.PROCESSING, outcome);
        assertEquals(TransactionStatus.PROCESSING, statusOf(transaction));
    }

    @Test
    void aTransactionOfAUserThatIsNotActiveFails() {
        for (UserStatus status : new UserStatus[]{UserStatus.INACTIVE, UserStatus.BLOCKED}) {
            Transaction transaction = transaction(userWith(status), TransactionStatus.PENDING);

            ValidationOutcome outcome = service.validate(transaction.id(), transaction.userId());

            assertEquals(ValidationOutcome.FAILED, outcome, "user " + status);
            assertEquals(TransactionStatus.FAILED, statusOf(transaction), "user " + status);
        }
    }

    @Test
    void aTransactionOfAUserThatDoesNotExistFails() {
        Transaction transaction = transaction(UUID.randomUUID(), TransactionStatus.PENDING);

        assertEquals(ValidationOutcome.FAILED, service.validate(transaction.id(), transaction.userId()));
        assertEquals(TransactionStatus.FAILED, statusOf(transaction));
    }

    @Test
    void theCacheIsReadFirstAndTheDatabaseIsNotTouchedWhenItHasTheStatus() {
        UUID userId = userWith(UserStatus.ACTIVE);
        cache.put(userId, UserStatus.ACTIVE);
        Transaction transaction = transaction(userId, TransactionStatus.PENDING);

        service.validate(transaction.id(), userId);

        assertEquals(0, users.findByIdCalls);
        assertEquals(TransactionStatus.PROCESSING, statusOf(transaction));
    }

    @Test
    void anEntryInTheCacheIsTrustedEvenWhenItDiffersFromTheDatabase() {
        // Documented in ADR 0007: the cache can be briefly stale, and what it holds wins
        UUID blockedInCache = userWith(UserStatus.ACTIVE);
        cache.put(blockedInCache, UserStatus.BLOCKED);
        UUID activeInCache = userWith(UserStatus.BLOCKED);
        cache.put(activeInCache, UserStatus.ACTIVE);

        Transaction first = transaction(blockedInCache, TransactionStatus.PENDING);
        Transaction second = transaction(activeInCache, TransactionStatus.PENDING);
        service.validate(first.id(), blockedInCache);
        service.validate(second.id(), activeInCache);

        assertEquals(TransactionStatus.FAILED, statusOf(first));
        assertEquals(TransactionStatus.PROCESSING, statusOf(second));
    }

    @Test
    void withoutAnEntryInTheCacheTheDatabaseIsReadAndTheCacheIsFilled() {
        UUID userId = userWith(UserStatus.ACTIVE);
        Transaction transaction = transaction(userId, TransactionStatus.PENDING);

        service.validate(transaction.id(), userId);

        assertEquals(1, users.findByIdCalls);
        assertEquals(UserStatus.ACTIVE, cache.entries.get(userId));
    }

    @Test
    void aUserThatDoesNotExistIsNotCached() {
        UUID unknown = UUID.randomUUID();
        Transaction transaction = transaction(unknown, TransactionStatus.PENDING);

        service.validate(transaction.id(), unknown);

        assertFalse(cache.entries.containsKey(unknown));
    }

    @Test
    void theSameEventDeliveredTwiceValidatesOnlyOnce() {
        Transaction transaction = transaction(userWith(UserStatus.ACTIVE), TransactionStatus.PENDING);

        assertEquals(ValidationOutcome.PROCESSING, service.validate(transaction.id(), transaction.userId()));
        assertEquals(ValidationOutcome.NOT_PENDING, service.validate(transaction.id(), transaction.userId()));

        assertEquals(TransactionStatus.PROCESSING, statusOf(transaction));
    }

    @Test
    void aTransactionThatWasAlreadyHandledIsNotTouched() {
        UUID userId = userWith(UserStatus.ACTIVE);
        for (TransactionStatus status : new TransactionStatus[]{TransactionStatus.PROCESSING, TransactionStatus.COMPLETED, TransactionStatus.FAILED}) {
            Transaction transaction = transaction(userId, status);

            assertEquals(ValidationOutcome.NOT_PENDING, service.validate(transaction.id(), userId), "status " + status);
            assertEquals(status, statusOf(transaction), "status " + status);
        }
    }

    @Test
    void aTransactionThatDoesNotExistChangesNothing() {
        UUID userId = userWith(UserStatus.ACTIVE);

        assertEquals(ValidationOutcome.NOT_PENDING, service.validate(UUID.randomUUID(), userId));
    }
}
