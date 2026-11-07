package com.event.producer.adapter.messaging.click;

import java.time.Instant;
import java.util.UUID;

/**
 * The JSON shape of a click event on Kafka. It is the contract between producer and consumer,
 * kept separate from the domain {@code Click} so the message format can evolve on its own.
 */
public record ClickMessage(UUID id, String adId, String userId, Instant clickedAt) {
}
