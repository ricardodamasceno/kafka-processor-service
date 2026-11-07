package com.event.producer.adapter.web.click;

import com.event.producer.domain.click.Click;

import java.time.Instant;
import java.util.UUID;

/** JSON body returned by the API for a click. */
public record ClickResponse(UUID id, String adId, String userId, Instant clickedAt) {

    static ClickResponse from(Click click) {
        return new ClickResponse(click.id(), click.adId(), click.userId(), click.clickedAt());
    }
}
