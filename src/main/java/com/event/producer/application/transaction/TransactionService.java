package com.event.producer.application.transaction;

import com.event.producer.application.shared.NotFoundException;
import com.event.producer.application.user.UserService;
import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionRepository;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import com.event.producer.domain.user.UserRepository;
import com.event.producer.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for transactions: orchestrates the transaction operations using the domain ports.
 * <p>
 * A transaction is created as {@code PENDING}. What happens next is driven by the changes of the
 * {@code transactions} table, which Debezium publishes to Kafka: this service never calls Kafka.
 * No Spring annotations; it is wired in {@code config.ServiceConfig}.
 */
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * Validates the data (the {@link Transaction} constructor enforces the domain rules), checks that the user exists,
     * assigns an id and saves the transaction as {@code PENDING}.
     * <p>
     * Only the existence of the user is checked here, because the transaction references the user and a row for a user
     * that does not exist makes no sense. Whether the user is active is not checked: that is part of the validation of
     * the {@code PENDING} transaction ({@link #validate}), which ends with the transaction
     * {@code FAILED} if the user is not active.
     *
     * @throws NotFoundException when the user does not exist
     */
    public Transaction create(UUID userId, String accountId, TransactionType type, BigDecimal amount, Instant occurredAt) {
        Transaction transaction = new Transaction(
                UUID.randomUUID(), userId, accountId, type, amount, occurredAt, TransactionStatus.PENDING);

        if (userRepository.findById(userId).isEmpty()) {
            throw new NotFoundException("User " + userId + " not found");
        }
        return repository.save(transaction);
    }

    /**
     * Validates a created transaction: the first step of its life, while it is {@code PENDING}. Carrying the
     * transaction out is a later step, which starts when it reaches {@code PROCESSING}.
     * <p>
     * The user has to be {@code ACTIVE}, and the status is read from the cache first and from the database when the
     * cache has no entry. If the user is active the transaction moves from {@code PENDING} to {@code PROCESSING},
     * otherwise (including a user that does not exist) it moves to {@code FAILED}.
     * <p>
     * The move is atomic and only happens while the transaction is still {@code PENDING}, so a transaction is
     * validated once even if the same event is delivered twice or by two instances.
     */
    public ValidationOutcome validate(UUID transactionId, UUID userId) {
        boolean userIsActive = userService.statusOf(userId).filter(status -> status == UserStatus.ACTIVE).isPresent();
        TransactionStatus newStatus = userIsActive ? TransactionStatus.PROCESSING : TransactionStatus.FAILED;

        if (!TransactionStatus.PENDING.canMoveTo(newStatus)) {
            throw new DomainValidationException("A transaction cannot move from PENDING to " + newStatus);
        }
        if (!repository.moveStatus(transactionId, TransactionStatus.PENDING, newStatus)) {
            return ValidationOutcome.NOT_PENDING;
        }
        return userIsActive ? ValidationOutcome.PROCESSING : ValidationOutcome.FAILED;
    }

    public Transaction get(UUID id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    /** Lists one page of transactions, most recent first. {@code page} starts at 0. */
    public List<Transaction> list(int page, int size) {
        return repository.findAll(page, size);
    }

    /** Replaces the data of a transaction. The owner and the status are not part of the request, so they are kept. */
    public Transaction update(UUID id, String accountId, TransactionType type, BigDecimal amount, Instant occurredAt) {
        Transaction existing = get(id);
        return repository.save(
                new Transaction(id, existing.userId(), accountId, type, amount, occurredAt, existing.status()));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("Transaction " + id + " not found");
    }
}
