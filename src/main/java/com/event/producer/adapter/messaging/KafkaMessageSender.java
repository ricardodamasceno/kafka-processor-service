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
 * Waiting makes "accepted" honest: when the API answers 202, the event is already stored in Kafka
 * (with {@code acks=all}, on several replicas). The cost is a few milliseconds of latency per request.
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
        String payload = codec.toJson(message);
        try {
            kafkaTemplate.send(topic, key, payload).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("Interrupted while publishing to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new PublishFailedException("Could not publish to " + topic, e);
        }
    }
}
