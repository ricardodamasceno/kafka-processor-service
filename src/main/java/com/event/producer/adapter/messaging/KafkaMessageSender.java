package com.event.producer.adapter.messaging;

import com.event.producer.application.shared.PublishFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends a message to a topic and waits for the broker acknowledgement.
 * <p>
 * Waiting makes "published" honest: when {@code send} returns, the event is stored in Kafka (with
 * {@code acks=all}, on several replicas). It also lets a failure reach the caller, so a consumer that
 * publishes does not commit its offset for a message whose event was lost.
 */
@Component
@RequiredArgsConstructor
public class KafkaMessageSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMessageCodec codec;
    private final MessagingProperties properties;

    /**
     * @param key the message key; messages with the same key go to the same partition, so they are consumed in order
     */
    public void send(String topic, String key, Object message) {
        try {
            kafkaTemplate.send(topic, key, codec.toJson(message)).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("Interrupted while publishing to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new PublishFailedException("Could not publish to " + topic, e);
        }
    }
}
