package com.event.producer.adapter.messaging.click;

import com.event.producer.domain.click.Click;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Converts between the domain {@link Click} and the Kafka {@link ClickMessage}. Pure functions, so static. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClickMessageMapper {

    static ClickMessage toMessage(Click click) {
        return new ClickMessage(click.id(), click.adId(), click.userId(), click.clickedAt());
    }

    /** Rebuilds the domain object, so the domain rules are checked again on the consumer side. */
    static Click toDomain(ClickMessage message) {
        return new Click(message.id(), message.adId(), message.userId(), message.clickedAt());
    }
}
