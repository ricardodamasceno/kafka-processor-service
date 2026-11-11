package com.event.producer.application.notification;

import com.event.producer.domain.notification.Notification;
import com.event.producer.domain.notification.TransactionOutcome;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Application service of the notification context. No Spring annotations; it is wired in {@code config.ServiceConfig}.
 */
@RequiredArgsConstructor
public class NotificationService {

    private final Notifier notifier;

    /** Tells the user how their transaction ended. */
    public void notifyOutcome(UUID userId, UUID transactionId, TransactionOutcome outcome) {
        notifier.send(new Notification(userId, transactionId, outcome));
    }
}
