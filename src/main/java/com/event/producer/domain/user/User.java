package com.event.producer.domain.user;

import com.event.producer.domain.shared.DomainValidationException;
import lombok.NonNull;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A person who owns transactions.
 * <p>
 * Domain entity: an immutable record. The name is trimmed, and the email is trimmed and lower-cased, so the same
 * address is always stored the same way and two emails that differ only in case are the same user.
 * Null checks come from Lombok's {@code @NonNull}; the business rules stay in the constructor.
 * The {@link UserStatus} says where the user is in their life.
 */
public record User(
        @NonNull UUID id,
        @NonNull String name,
        @NonNull String email,
        @NonNull Instant createdAt,
        @NonNull UserStatus status) {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int EMAIL_MAX_LENGTH = 320;

    /** One address with a non-empty local part and a domain with at least one dot. It is a sanity check, not the RFC. */
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    public User {
        name = name.strip();
        email = email.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new DomainValidationException("name must not be blank");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name must have at most " + NAME_MAX_LENGTH + " characters");
        }
        if (email.length() > EMAIL_MAX_LENGTH) {
            throw new DomainValidationException("email must have at most " + EMAIL_MAX_LENGTH + " characters");
        }
        if (!EMAIL_FORMAT.matcher(email).matches()) {
            throw new DomainValidationException("email is not valid");
        }
    }
}
