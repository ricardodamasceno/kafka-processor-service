package com.event.producer.application.click;

import com.event.producer.application.shared.NotFoundException;
import com.event.producer.domain.click.Click;
import com.event.producer.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickServiceTest {

    private static final Instant T1 = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-07T11:00:00Z");

    private final ClickService service = new ClickService(new InMemoryClickRepository());

    @Test
    void createsAndGetsAClick() {
        Click created = service.create("ad-1", "user-1", T1);

        assertEquals(created, service.get(created.id()));
    }

    @Test
    void createRejectsInvalidData() {
        assertThrows(DomainValidationException.class, () -> service.create(" ", "user-1", T1));
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        assertThrows(NotFoundException.class, () -> service.get(UUID.randomUUID()));
    }

    @Test
    void updateReplacesTheData() {
        Click created = service.create("ad-1", "user-1", T1);

        Click updated = service.update(created.id(), "ad-2", "user-1", T2);

        assertEquals("ad-2", updated.adId());
        assertEquals(updated, service.get(created.id()));
    }

    @Test
    void updateThrowsNotFoundForUnknownId() {
        assertThrows(NotFoundException.class, () -> service.update(UUID.randomUUID(), "ad-1", "user-1", T1));
    }

    @Test
    void deleteRemovesTheClick() {
        Click created = service.create("ad-1", "user-1", T1);

        service.delete(created.id());

        assertThrows(NotFoundException.class, () -> service.get(created.id()));
        assertThrows(NotFoundException.class, () -> service.delete(created.id()));
    }

    @Test
    void listReturnsMostRecentFirstAndPaginates() {
        Click older = service.create("ad-1", "user-1", T1);
        Click newer = service.create("ad-1", "user-2", T2);

        assertEquals(List.of(newer, older), service.list(0, 10));
        assertEquals(List.of(newer), service.list(0, 1));
        assertEquals(List.of(older), service.list(1, 1));
        assertTrue(service.list(5, 10).isEmpty());
    }
}
