package com.event.producer.adapter.persistence.user;

import com.event.producer.application.shared.ConflictException;
import com.event.producer.domain.user.User;
import com.event.producer.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements the domain port {@link UserRepository} using Spring Data JPA.
 * The conversion between domain and database objects is delegated to {@link UserMapper}.
 */
@Repository
@RequiredArgsConstructor
class UserRepositoryAdapter implements UserRepository {

    /** The unique index on {@code lower(email)}, created by migration V2. */
    private static final String EMAIL_INDEX = "uq_users_email";

    private final UserJpaRepository jpaRepository;

    @Override
    public User save(User user) {
        try {
            return UserMapper.toDomain(jpaRepository.save(UserMapper.toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            if (isEmailViolation(e)) {
                throw new ConflictException("A user with this email already exists", e);
            }
            throw e;
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmailIgnoringCase(email);
    }

    private static boolean isEmailViolation(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.contains(EMAIL_INDEX);
    }
}
