package com.event.producer.application.transaction;

import com.event.producer.application.shared.NotFoundException;
import com.event.producer.application.user.UserService;
import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionRepository;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserRepository;
import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Instant T1 = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-07T11:00:00Z");
    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Mock
    private TransactionRepository repository;
    @Mock
    private UserRepository users;
    @Mock
    private UserService userService;
    @InjectMocks
    private TransactionService service;

    private final UUID userId = UUID.randomUUID();

    private void saveReturnsItsArgument() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void userExists(UserStatus status) {
        when(users.findById(userId)).thenReturn(
                Optional.of(new User(userId, "Alice", "alice@example.com", T1, status)));
    }

    private Transaction transaction(TransactionStatus status) {
        return new Transaction(UUID.randomUUID(), userId, "acc-1", TransactionType.DEPOSIT, TEN, T1, status);
    }

    // create

    @Test
    void createSavesAPendingTransactionOwnedByTheUser() {
        userExists(UserStatus.ACTIVE);
        saveReturnsItsArgument();

        Transaction created = service.create(userId, "acc-1", TransactionType.DEPOSIT, TEN, T1);

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(saved.capture());
        assertEquals(created, saved.getValue());
        assertEquals(TransactionStatus.PENDING, created.status());
        assertEquals(userId, created.userId());
    }

    @Test
    void createRejectsAnUnknownUserAndStoresNothing() {
        when(users.findById(userId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> service.create(userId, "acc-1", TransactionType.DEPOSIT, TEN, T1));

        assertTrue(exception.getMessage().contains(userId.toString()));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIVE", "BLOCKED"})
    void createAcceptsAUserThatIsNotActiveAndLeavesItToTheValidation(UserStatus status) {
        // The status of the user is not checked here: the validation of the PENDING transaction does it, and fails it
        userExists(status);
        saveReturnsItsArgument();

        Transaction created = service.create(userId, "acc-1", TransactionType.DEPOSIT, TEN, T1);

        assertEquals(TransactionStatus.PENDING, created.status());
        verifyNoInteractions(userService);
    }

    @Test
    void createRejectsInvalidDataBeforeLookingForTheUser() {
        // The data is checked first, so a bad request is a 400 even when the user does not exist
        assertThrows(DomainValidationException.class,
                () -> service.create(userId, " ", TransactionType.DEPOSIT, TEN, T1));
        assertThrows(DomainValidationException.class,
                () -> service.create(userId, "acc-1", TransactionType.DEPOSIT, BigDecimal.ZERO, T1));

        verifyNoInteractions(users, repository);
    }

    // validate

    @Test
    void aTransactionOfAnActiveUserMovesToProcessing() {
        UUID transactionId = UUID.randomUUID();
        when(userService.statusOf(userId)).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(repository.moveStatus(transactionId, TransactionStatus.PENDING, TransactionStatus.PROCESSING))
                .thenReturn(true);

        assertEquals(ValidationOutcome.PROCESSING, service.validate(transactionId, userId));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIVE", "BLOCKED"})
    void aTransactionOfAUserThatIsNotActiveFails(UserStatus status) {
        UUID transactionId = UUID.randomUUID();
        when(userService.statusOf(userId)).thenReturn(Optional.of(status));
        when(repository.moveStatus(transactionId, TransactionStatus.PENDING, TransactionStatus.FAILED))
                .thenReturn(true);

        assertEquals(ValidationOutcome.FAILED, service.validate(transactionId, userId));
    }

    @Test
    void aTransactionOfAUserThatDoesNotExistFails() {
        UUID transactionId = UUID.randomUUID();
        when(userService.statusOf(userId)).thenReturn(Optional.empty());
        when(repository.moveStatus(transactionId, TransactionStatus.PENDING, TransactionStatus.FAILED))
                .thenReturn(true);

        assertEquals(ValidationOutcome.FAILED, service.validate(transactionId, userId));
    }

    @Test
    void theSameEventDeliveredTwiceValidatesOnlyOnce() {
        // The conditional update decides: the first call moves the transaction, the second finds it no longer PENDING
        UUID transactionId = UUID.randomUUID();
        when(userService.statusOf(userId)).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(repository.moveStatus(transactionId, TransactionStatus.PENDING, TransactionStatus.PROCESSING))
                .thenReturn(true, false);

        assertEquals(ValidationOutcome.PROCESSING, service.validate(transactionId, userId));
        assertEquals(ValidationOutcome.NOT_PENDING, service.validate(transactionId, userId));
    }

    @Test
    void aTransactionThatIsNotPendingOrDoesNotExistIsLeftAlone() {
        UUID transactionId = UUID.randomUUID();
        when(userService.statusOf(userId)).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(repository.moveStatus(transactionId, TransactionStatus.PENDING, TransactionStatus.PROCESSING))
                .thenReturn(false);

        assertEquals(ValidationOutcome.NOT_PENDING, service.validate(transactionId, userId));
        verify(repository, never()).save(any());
    }

    // complete

    @Test
    void aProcessingTransactionIsCompleted() {
        UUID transactionId = UUID.randomUUID();
        when(repository.moveStatus(transactionId, TransactionStatus.PROCESSING, TransactionStatus.COMPLETED))
                .thenReturn(true);

        assertTrue(service.complete(transactionId));
    }

    @Test
    void aTransactionThatIsNotProcessingOrDoesNotExistIsNotCompleted() {
        UUID transactionId = UUID.randomUUID();
        when(repository.moveStatus(transactionId, TransactionStatus.PROCESSING, TransactionStatus.COMPLETED))
                .thenReturn(false);

        assertFalse(service.complete(transactionId));
        verify(repository, never()).save(any());
    }

    // get, list, update, delete

    @Test
    void getReturnsTheTransaction() {
        Transaction transaction = transaction(TransactionStatus.PENDING);
        when(repository.findById(transaction.id())).thenReturn(Optional.of(transaction));

        assertEquals(transaction, service.get(transaction.id()));
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(id));
    }

    @Test
    void listReturnsThePageTheRepositoryGives() {
        List<Transaction> page = List.of(transaction(TransactionStatus.PENDING), transaction(TransactionStatus.FAILED));
        when(repository.findAll(1, 2)).thenReturn(page);

        assertEquals(page, service.list(1, 2));
    }

    @Test
    void updateReplacesTheDataAndKeepsTheOwnerAndTheStatus() {
        Transaction existing = transaction(TransactionStatus.COMPLETED);
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        saveReturnsItsArgument();

        Transaction updated = service.update(existing.id(), "acc-2", TransactionType.WITHDRAWAL, new BigDecimal("25.50"), T2);

        assertEquals(new Transaction(existing.id(), userId, "acc-2", TransactionType.WITHDRAWAL,
                new BigDecimal("25.50"), T2, TransactionStatus.COMPLETED), updated);
        verify(repository).save(updated);
    }

    @Test
    void updateThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.update(id, "acc-1", TransactionType.DEPOSIT, TEN, T1));
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRemovesTheTransaction() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(repository).deleteById(id);
    }

    @Test
    void deleteThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.delete(id));
        verify(repository, never()).deleteById(any());
    }
}
