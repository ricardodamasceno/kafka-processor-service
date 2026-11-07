package com.event.producer.application.click;

import com.event.producer.domain.click.Click;
import com.event.producer.domain.click.ClickRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Test double that implements the domain port with a map. Because services depend on the
 * {@link ClickRepository} interface, they can be tested without a database.
 */
class InMemoryClickRepository implements ClickRepository {

    private final Map<UUID, Click> store = new LinkedHashMap<>();

    @Override
    public Click save(Click click) {
        store.put(click.id(), click);
        return click;
    }

    @Override
    public Optional<Click> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Click> findAll(int page, int size) {
        List<Click> sorted = new ArrayList<>(store.values());
        sorted.sort(Comparator.comparing(Click::clickedAt).reversed());
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
