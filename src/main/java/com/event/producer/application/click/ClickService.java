package com.event.producer.application.click;

import com.event.producer.application.shared.NotFoundException;
import com.event.producer.domain.click.Click;
import com.event.producer.domain.click.ClickRepository;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for clicks: orchestrates the click operations using the domain port.
 * <p>
 * It has no Spring annotations; it is wired in {@code config.ServiceConfig}, so the application
 * layer stays independent from the framework.
 */
@RequiredArgsConstructor
public class ClickService {

    private final ClickRepository repository;

    public Click create(String adId, String userId, Instant clickedAt) {
        return repository.save(new Click(UUID.randomUUID(), adId, userId, clickedAt));
    }

    public Click get(UUID id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    /** Lists one page of clicks, most recent first. {@code page} starts at 0. */
    public List<Click> list(int page, int size) {
        return repository.findAll(page, size);
    }

    public Click update(UUID id, String adId, String userId, Instant clickedAt) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        return repository.save(new Click(id, adId, userId, clickedAt));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("Click " + id + " not found");
    }
}
