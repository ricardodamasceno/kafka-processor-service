package com.event.producer.adapter.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** Spring Data interface; an implementation detail of this adapter. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    /** Compares with {@code lower()}, which is what the unique index on {@code lower(email)} covers. */
    @Query("select count(u) > 0 from UserJpaEntity u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoringCase(@Param("email") String email);
}
