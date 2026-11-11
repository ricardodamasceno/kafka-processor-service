package com.event.producer.application.notification;

import com.event.producer.domain.notification.Notification;
import com.event.producer.domain.notification.TransactionOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private Notifier notifier;
    @InjectMocks
    private NotificationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    @Test
    void theUserIsToldThatTheTransactionCompleted() {
        service.notifyOutcome(userId, transactionId, TransactionOutcome.COMPLETED);

        verify(notifier).send(new Notification(userId, transactionId, TransactionOutcome.COMPLETED));
    }

    @Test
    void theUserIsToldThatTheTransactionFailed() {
        service.notifyOutcome(userId, transactionId, TransactionOutcome.FAILED);

        verify(notifier).send(new Notification(userId, transactionId, TransactionOutcome.FAILED));
    }
}
