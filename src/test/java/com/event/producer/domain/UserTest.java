package com.event.producer.domain;

import com.event.producer.domain.shared.DomainValidationException;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Domain tests need no Spring and no database: they run in milliseconds. */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private static User with(String name, String email) {
        return new User(UUID.randomUUID(), name, email, NOW, UserStatus.ACTIVE);
    }

    @Test
    void trimsTheNameAndNormalizesTheEmail() {
        User user = with("  Alice Silva  ", "  Alice.Silva@Example.COM ");

        assertEquals("Alice Silva", user.name());
        assertEquals("alice.silva@example.com", user.email());
    }

    @Test
    void rejectsABlankName() {
        assertThrows(DomainValidationException.class, () -> with("", "a@example.com"));
        assertThrows(DomainValidationException.class, () -> with("   ", "a@example.com"));
    }

    @Test
    void acceptsANameOf200CharactersAndRejectsLongerOnes() {
        assertDoesNotThrow(() -> with("a".repeat(200), "a@example.com"));
        assertThrows(DomainValidationException.class, () -> with("a".repeat(201), "a@example.com"));
    }

    @Test
    void rejectsInvalidEmails() {
        List<String> invalid = List.of("", "   ", "alice", "alice@", "@example.com", "alice@example",
                "a b@example.com", "alice@@example.com", "alice@example..com", "alice@.example.com", "alice@example.com.");

        for (String email : invalid) {
            assertThrows(DomainValidationException.class, () -> with("Alice", email), "should reject: '" + email + "'");
        }
    }

    @Test
    void acceptsCommonEmails() {
        assertDoesNotThrow(() -> with("Alice", "a@b.co"));
        assertDoesNotThrow(() -> with("Alice", "first.last+tag@sub.example.org"));
    }

    @Test
    void rejectsAnEmailLongerThan320Characters() {
        String tooLong = "a".repeat(310) + "@example.com";

        assertThrows(DomainValidationException.class, () -> with("Alice", tooLong));
    }

    @Test
    void withStatusReturnsACopyWithOnlyTheStatusChanged() {
        User user = with("Alice", "alice@example.com");

        User blocked = user.withStatus(UserStatus.BLOCKED);

        assertEquals(UserStatus.BLOCKED, blocked.status());
        assertEquals(user.id(), blocked.id());
        assertEquals(user.name(), blocked.name());
        assertEquals(user.email(), blocked.email());
        assertEquals(user.createdAt(), blocked.createdAt());
        assertEquals(UserStatus.ACTIVE, user.status());
    }

    @Test
    void withStatusRejectsANullStatus() {
        assertThrows(NullPointerException.class, () -> with("Alice", "alice@example.com").withStatus(null));
    }

    @Test
    void rejectsNullFields() {
        // Lombok's @NonNull makes the constructor throw NullPointerException
        assertThrows(NullPointerException.class, () -> with(null, "a@example.com"));
        assertThrows(NullPointerException.class, () -> with("Alice", null));
        assertThrows(NullPointerException.class, () -> new User(null, "Alice", "a@example.com", NOW, UserStatus.ACTIVE));
        assertThrows(NullPointerException.class, () -> new User(UUID.randomUUID(), "Alice", "a@example.com", null, UserStatus.ACTIVE));
        assertThrows(NullPointerException.class, () -> new User(UUID.randomUUID(), "Alice", "a@example.com", NOW, null));
    }
}
