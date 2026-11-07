package com.event.producer.application.click;

import com.event.producer.application.shared.NotFoundException;
import com.event.producer.domain.click.Click;
import com.event.producer.domain.click.ClickRepository;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for clicks: orchestrates the click operations using the domain ports.
 * <p>
 * Creation is asynchronous: {@link #requestCreation} validates and publishes an event, and
 * {@link #persist} (called by the event consumer) writes the click to the database.
 * <p>
 * It has no Spring annotations; it is wired in {@code config.ServiceConfig}, so the application
 * layer stays independent from the framework.
 */
@RequiredArgsConstructor
public class ClickService {

    private final ClickRepository repository;
    private final ClickEventPublisher publisher;

    /**
     * Validates the data (the {@link Click} constructor enforces the domain rules), assigns an id and
     * publishes the creation event. Nothing is written to the database here.
     *
     * @return the click that was accepted; it will exist in the database once the consumer processes the event
     */
    public Click requestCreation(String adId, String userId, Instant clickedAt) {
        Click click = new Click(UUID.randomUUID(), adId, userId, clickedAt);
        publisher.publishCreationRequested(click);
        return click;
    }

    /**
     * Writes a click to the database. Called by the event consumer. Idempotent: the id comes with the
     * event, so processing the same event twice (at-least-once delivery) leaves a single row.
     */
    public Click persist(Click click) {
        return repository.save(click);
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
