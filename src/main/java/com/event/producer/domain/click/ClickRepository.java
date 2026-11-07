package com.event.producer.domain.click;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (interface) that describes what the application needs from a click store.
 * <p>
 * It lives in the domain, but the implementation lives in the outer layer
 * ({@code adapter.persistence}). This is the Dependency Inversion Principle: the inner layers
 * define the contract, and the outer layers implement it.
 */
public interface ClickRepository {

    Click save(Click click);

    Optional<Click> findById(UUID id);

    /** Returns one page of clicks, most recent first. {@code page} starts at 0. */
    List<Click> findAll(int page, int size);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}
