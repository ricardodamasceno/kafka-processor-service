package com.event.producer.adapter.persistence.click;

import com.event.producer.domain.click.Click;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Converts between the domain {@link Click} and the database {@link ClickJpaEntity}.
 * Its only responsibility is this translation, so the repository adapter can focus on persistence.
 * <p>
 * The conversion is a pure function (no state, no dependencies), so the methods are static and the
 * class is not a Spring bean.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClickMapper {

    static ClickJpaEntity toEntity(Click click) {
        return new ClickJpaEntity(click.id(), click.adId(), click.userId(), click.clickedAt());
    }

    static Click toDomain(ClickJpaEntity entity) {
        return new Click(entity.getId(), entity.getAdId(), entity.getUserId(), entity.getClickedAt());
    }
}
