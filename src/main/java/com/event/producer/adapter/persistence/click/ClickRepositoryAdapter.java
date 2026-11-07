package com.event.producer.adapter.persistence.click;

import com.event.producer.domain.click.Click;
import com.event.producer.domain.click.ClickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the domain port {@link ClickRepository} using Spring Data JPA.
 * The conversion between domain and database objects is delegated to {@link ClickMapper}.
 */
@Repository
@RequiredArgsConstructor
class ClickRepositoryAdapter implements ClickRepository {

    private final ClickJpaRepository jpaRepository;

    @Override
    public Click save(Click click) {
        return ClickMapper.toDomain(jpaRepository.save(ClickMapper.toEntity(click)));
    }

    @Override
    public Optional<Click> findById(UUID id) {
        return jpaRepository.findById(id).map(ClickMapper::toDomain);
    }

    @Override
    public List<Click> findAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "clickedAt"));
        return jpaRepository.findAll(pageRequest).stream().map(ClickMapper::toDomain).toList();
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
