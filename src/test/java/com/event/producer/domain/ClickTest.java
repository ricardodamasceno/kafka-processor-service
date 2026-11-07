package com.event.producer.domain;

import com.event.producer.domain.click.Click;
import com.event.producer.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ClickTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    @Test
    void rejectsBlankAdAndUser() {
        assertThrows(DomainValidationException.class, () -> new Click(UUID.randomUUID(), " ", "user-1", NOW));
        assertThrows(DomainValidationException.class, () -> new Click(UUID.randomUUID(), "ad-1", "", NOW));
    }

    @Test
    void rejectsNullFields() {
        // Lombok's @NonNull makes the constructor throw NullPointerException
        assertThrows(NullPointerException.class, () -> new Click(null, "ad-1", "user-1", NOW));
        assertThrows(NullPointerException.class, () -> new Click(UUID.randomUUID(), null, "user-1", NOW));
        assertThrows(NullPointerException.class, () -> new Click(UUID.randomUUID(), "ad-1", "user-1", null));
    }
}
