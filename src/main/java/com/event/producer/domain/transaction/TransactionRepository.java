package com.event.producer.domain.transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (interface) that describes what the application needs from a transaction store.
 * The implementation lives in {@code adapter.persistence.transaction}.
 */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(UUID id);

    /** Returns one page of transactions, most recent first. {@code page} starts at 0. */
    List<Transaction> findAll(int page, int size);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}
