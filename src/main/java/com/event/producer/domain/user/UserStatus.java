package com.event.producer.domain.user;

/**
 * Where a user is in their life. A newly registered user is {@code ACTIVE}.
 * <ul>
 *   <li>{@code ACTIVE}: the user can operate normally.</li>
 *   <li>{@code INACTIVE}: the account is no longer in use.</li>
 *   <li>{@code BLOCKED}: the account is suspended, for example after a fraud alert.</li>
 * </ul>
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}
