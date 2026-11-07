package com.event.producer.adapter.messaging.click;

import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.click.ClickService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes click events and writes the clicks to the database.
 * <p>
 * Delivery is at-least-once: if the service fails before the offset is committed, the message is
 * delivered again. That is safe because {@code ClickService.persist} is idempotent (the id is part of
 * the message). All instances of the application share the group {@code click-persister}, so each
 * partition is processed by only one of them.
 */
@Component
@RequiredArgsConstructor
class ClickKafkaConsumer {

    private final ClickService service;
    private final JsonMessageCodec codec;

    @KafkaListener(topics = "${app.messaging.topics.clicks}", groupId = "click-persister")
    void onMessage(String payload) {
        ClickMessage message = codec.fromJson(payload, ClickMessage.class);
        service.persist(ClickMessageMapper.toDomain(message));
    }
}
