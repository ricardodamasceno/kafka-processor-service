package com.event.producer.adapter.messaging.notification;

import com.event.producer.adapter.messaging.InvalidMessageException;
import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.notification.NotificationService;
import com.event.producer.domain.notification.TransactionOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransactionOutcomeConsumerTest {

    private static final UUID ID = UUID.fromString("3f2b8f36-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("a1111111-0000-4000-8000-000000000001");

    @Mock
    private NotificationService notificationService;

    private TransactionOutcomeConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TransactionOutcomeConsumer(new JsonMessageCodec(JsonMapper.builder().build()), notificationService);
    }

    /** The whole event, with the fields this context does not read, as the transaction context publishes it. */
    private static String event(String type) {
        return "{\"eventId\":\"" + ID + ":X\",\"type\":\"" + type + "\",\"transaction\":{\"id\":\"" + ID
                + "\",\"userId\":\"" + USER_ID + "\",\"accountId\":\"acc-1\",\"type\":\"DEPOSIT\",\"amount\":10.50,"
                + "\"occurredAt\":\"2026-11-10T12:00:00Z\",\"status\":\"X\"}}";
    }

    @Test
    void aCompletedTransactionNotifiesTheUser() {
        consumer.onMessage(event("TRANSACTION_COMPLETED"));

        verify(notificationService).notifyOutcome(USER_ID, ID, TransactionOutcome.COMPLETED);
    }

    @Test
    void aFailedTransactionNotifiesTheUser() {
        consumer.onMessage(event("TRANSACTION_FAILED"));

        verify(notificationService).notifyOutcome(USER_ID, ID, TransactionOutcome.FAILED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSACTION_PROCESSING", "TRANSACTION_CREATED", "SOMETHING_NEW"})
    void otherEventsNotifyNobody(String type) {
        consumer.onMessage(event(type));

        verifyNoInteractions(notificationService);
    }

    @Test
    void aMessageThatCannotBeReadIsInvalid() {
        assertThrows(InvalidMessageException.class, () -> consumer.onMessage("not json"));
        assertThrows(InvalidMessageException.class, () -> consumer.onMessage("{\"eventId\":\"x\"}"));
        verifyNoInteractions(notificationService);
    }

    @Test
    void anOutcomeWithoutTheUserIsInvalid() {
        String withoutUser = "{\"eventId\":\"x\",\"type\":\"TRANSACTION_FAILED\",\"transaction\":{\"id\":\"" + ID + "\"}}";

        assertThrows(InvalidMessageException.class, () -> consumer.onMessage(withoutUser));
        verifyNoInteractions(notificationService);
    }
}
