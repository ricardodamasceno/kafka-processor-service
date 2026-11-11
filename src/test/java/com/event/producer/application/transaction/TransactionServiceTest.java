package com.event.producer.application.transaction;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.application.shared.NotFoundException;
import com.event.producer.application.user.InMemoryUserRepository;
import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionServiceTest {

    private static final Instant T1 = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-07T11:00:00Z");
    private static final BigDecimal TEN = new BigDecimal("10.00");

    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final TransactionService service = new TransactionService(repository, users);

    private UUID userWith(UserStatus status) {
        User user = new User(UUID.randomUUID(), "Alice", UUID.randomUUID() + "@example.com", T1, status);
        users.save(user);
        return user.id();
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
    void createRejectsAUserThatIsNotActive() {
        UUID inactive = userWith(UserStatus.INACTIVE);
        UUID blocked = userWith(UserStatus.BLOCKED);

        assertThrows(ConflictException.class, () -> service.create(inactive, "acc-1", TransactionType.DEPOSIT, TEN, T1));
        assertThrows(ConflictException.class, () -> service.create(blocked, "acc-1", TransactionType.DEPOSIT, TEN, T1));
        assertTrue(service.list(0, 10).isEmpty());
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
}
