package com.event.producer.adapter.messaging.click;

import com.event.producer.adapter.messaging.KafkaMessageSender;
import com.event.producer.adapter.messaging.MessagingProperties;
import com.event.producer.application.click.ClickEventPublisher;
import com.event.producer.domain.click.Click;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Kafka implementation of the {@link ClickEventPublisher} port (the producer).
 * <p>
 * The message key is the click id: every event about the same click goes to the same partition,
 * which keeps them in order if updates and deletes become asynchronous later. Random UUIDs also
 * spread the load evenly across partitions.
 */
@Component
@RequiredArgsConstructor
class ClickKafkaPublisher implements ClickEventPublisher {

    private final KafkaMessageSender sender;
    private final MessagingProperties properties;

    @Override
    public void publishCreationRequested(Click click) {
        sender.send(properties.topics().clicks(), click.id().toString(), ClickMessageMapper.toMessage(click));
    }
}
