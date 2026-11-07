package com.event.producer.adapter.persistence.transaction;

import com.event.producer.domain.transaction.Transaction;
import com.event.producer.domain.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the domain port {@link TransactionRepository} using Spring Data JPA.
 * The conversion between domain and database objects is delegated to {@link TransactionMapper}.
 */
@Repository
@RequiredArgsConstructor
class TransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    @Override
    public Transaction save(Transaction transaction) {
        return TransactionMapper.toDomain(jpaRepository.save(TransactionMapper.toEntity(transaction)));
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jpaRepository.findById(id).map(TransactionMapper::toDomain);
    }

    @Override
    public List<Transaction> findAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return jpaRepository.findAll(pageRequest).stream().map(TransactionMapper::toDomain).toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
