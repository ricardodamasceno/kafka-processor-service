package com.event.producer.adapter.messaging;

import com.event.producer.domain.shared.DomainValidationException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * What happens when a consumer fails to process a message.
 * <ol>
 *   <li>Transient failures (for example the database is briefly down) are retried a few times.</li>
 *   <li>If it still fails, or if the message can never succeed (bad payload, broken domain rule),
 *       it is published to the topic's dead letter queue, so one bad message does not block the partition.</li>
 * </ol>
 * Spring Boot applies this handler to every {@code @KafkaListener}.
 */
@Configuration
class KafkaErrorHandlingConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate, MessagingProperties properties) {
        MessagingProperties.Topics topics = properties.topics();
        Map<String, String> dlqBySourceTopic = Map.of(topics.transactionsCdcEvents(), topics.transactionsCdcEventsDlq());

        // A negative partition lets Kafka choose it from the key, so the DLQ may have fewer partitions than the source.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, exception) -> new TopicPartition(dlqBySourceTopic.get(record.topic()), -1));

        FixedBackOff backOff = new FixedBackOff(properties.retry().interval().toMillis(), properties.retry().maxRetries());
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidMessageException.class, DomainValidationException.class);
        return handler;
    }
}
