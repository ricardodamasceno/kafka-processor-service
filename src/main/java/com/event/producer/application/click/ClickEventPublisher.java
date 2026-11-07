package com.event.producer.application.click;

import com.event.producer.domain.click.Click;

/**
 * Outbound port: how the application announces click events to the outside world.
 * <p>
 * Like the repository ports, it is an interface owned by the inner layers; the Kafka
 * implementation lives in {@code adapter.messaging.click}. The service does not know Kafka exists.
 */
public interface ClickEventPublisher {

    /**
     * Announces that the creation of a click was requested. Returns only after the broker has
     * acknowledged the event; throws {@link com.event.producer.application.shared.PublishFailedException}
     * otherwise.
     */
    void publishCreationRequested(Click click);
}
