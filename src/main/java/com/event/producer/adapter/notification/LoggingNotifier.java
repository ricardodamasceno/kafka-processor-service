package com.event.producer.adapter.notification;

import com.event.producer.application.notification.Notifier;
import com.event.producer.domain.notification.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implements the {@link Notifier} port by writing a log line. A study project has no e-mail or push provider, so this
 * stands in for one: replacing it is the only change needed to send real notifications.
 */
@Slf4j
@Component
class LoggingNotifier implements Notifier {

    @Override
    public void send(Notification notification) {
        switch (notification.outcome()) {
            case COMPLETED -> log.info("Notification to user {}: transaction {} completed",
                    notification.userId(), notification.transactionId());
            case FAILED -> log.info("Notification to user {}: transaction {} failed",
                    notification.userId(), notification.transactionId());
        }
    }
}
