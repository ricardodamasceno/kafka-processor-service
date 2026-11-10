package com.event.producer.application.transaction;

import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Test double that implements the domain port with a map. Because services depend on the
 * {@link TransactionRepository} interface, they can be tested without a database.
 */
class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<UUID, Transaction> store = new LinkedHashMap<>();

    @Override
    public Transaction save(Transaction transaction) {
        store.put(transaction.id(), transaction);
        return transaction;
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Transaction> findAll(int page, int size) {
        List<Transaction> sorted = new ArrayList<>(store.values());
        sorted.sort(Comparator.comparing(Transaction::occurredAt).reversed());
        int from = Math.min(page * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        return sorted.subList(from, to);
    }

    @Override
    public boolean existsById(UUID id) {
        return store.containsKey(id);
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }
}
