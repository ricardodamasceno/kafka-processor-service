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
 * Application service for transactions: orchestrates the transaction operations using the domain port.
 * No Spring annotations; it is wired in {@code config.ServiceConfig}.
 */
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;

    public Transaction create(String accountId, TransactionType type, BigDecimal amount, Instant occurredAt) {
        return repository.save(new Transaction(UUID.randomUUID(), accountId, type, amount, occurredAt));
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
