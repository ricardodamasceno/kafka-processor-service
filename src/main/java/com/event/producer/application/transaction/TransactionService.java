package com.event.producer.application.transaction;

import com.event.producer.application.shared.NotFoundException;
import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionRepository;
import com.event.producer.domain.transaction.TransactionType;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for transactions: orchestrates the transaction operations using the domain ports.
 * <p>
 * Creation is asynchronous: {@link #requestCreation} validates and publishes an event, and
 * {@link #persist} (called by the event consumer) writes the transaction to the database.
 * No Spring annotations; it is wired in {@code config.ServiceConfig}.
 */
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;
    private final TransactionEventPublisher publisher;

    /**
     * Validates the data (the {@link Transaction} constructor enforces the domain rules), assigns an id
     * and publishes the creation event. Nothing is written to the database here.
     *
     * @return the transaction that was accepted; it will exist in the database once the consumer processes the event
     */
    public Transaction requestCreation(String accountId, TransactionType type, BigDecimal amount, Instant occurredAt) {
        Transaction transaction = new Transaction(UUID.randomUUID(), accountId, type, amount, occurredAt);
        publisher.publishCreationRequested(transaction);
        return transaction;
    }

    /**
     * Writes a transaction to the database. Called by the event consumer. Idempotent: the id comes with
     * the event, so processing the same event twice (at-least-once delivery) leaves a single row.
     */
    public Transaction persist(Transaction transaction) {
        return repository.save(transaction);
    }

    public Transaction get(UUID id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    /** Lists one page of transactions, most recent first. {@code page} starts at 0. */
    public List<Transaction> list(int page, int size) {
        return repository.findAll(page, size);
    }

    public Transaction update(UUID id, String accountId, TransactionType type, BigDecimal amount, Instant occurredAt) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        return repository.save(new Transaction(id, accountId, type, amount, occurredAt));
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
