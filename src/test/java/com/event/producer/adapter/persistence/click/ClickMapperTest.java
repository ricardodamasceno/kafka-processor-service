package com.event.producer.adapter.persistence.click;

import com.event.producer.domain.click.Click;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClickMapperTest {

    @Test
    void convertsDomainToEntityAndBack() {
        Click click = new Click(UUID.randomUUID(), "ad-1", "user-1", Instant.parse("2026-11-07T10:00:00Z"));

        ClickJpaEntity entity = ClickMapper.toEntity(click);

        assertEquals(click.id(), entity.getId());
        assertEquals(click.adId(), entity.getAdId());
        assertEquals(click.userId(), entity.getUserId());
        assertEquals(click.clickedAt(), entity.getClickedAt());
        assertEquals(click, ClickMapper.toDomain(entity));
    }
}
