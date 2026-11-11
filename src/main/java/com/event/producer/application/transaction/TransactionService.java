package com.event.producer.application.transaction;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.application.shared.NotFoundException;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionRepository;
import com.event.producer.domain.transaction.TransactionStatus;
import com.event.producer.domain.transaction.TransactionType;
import com.event.producer.domain.user.User;
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

    /**
     * Validates the data (the {@link Transaction} constructor enforces the domain rules), checks that the user exists
     * and is active, assigns an id and saves the transaction as {@code PENDING}.
     *
     * @throws NotFoundException when the user does not exist
     * @throws ConflictException when the user is not {@code ACTIVE}
     */
    public Transaction create(UUID userId, String accountId, TransactionType type, BigDecimal amount, Instant occurredAt) {
        Transaction transaction = new Transaction(
                UUID.randomUUID(), userId, accountId, type, amount, occurredAt, TransactionStatus.PENDING);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
        if (user.status() != UserStatus.ACTIVE) {
            throw new ConflictException("User " + userId + " is " + user.status() + " and cannot create transactions");
        }
        return repository.save(transaction);
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
