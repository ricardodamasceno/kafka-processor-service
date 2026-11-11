package com.event.producer.adapter.messaging.transaction;

import com.event.producer.adapter.messaging.InvalidMessageException;
import com.event.producer.adapter.messaging.JsonMessageCodec;
import com.event.producer.application.movement.MovementService;
import com.event.producer.application.shared.ConflictException;
import com.event.producer.application.transaction.TransactionService;
import com.event.producer.domain.movement.MovementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionUpdatedConsumerTest {

    private static final UUID ID = UUID.fromString("3f2b8f36-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("a1111111-0000-4000-8000-000000000001");

    @Mock
    private MovementService movementService;
    @Mock
    private TransactionService transactionService;

    private TransactionUpdatedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TransactionUpdatedConsumer(new JsonMessageCodec(JsonMapper.builder().build()), movementService,
                transactionService);
    }

    private static String event(String type, String transactionType, String status) {
        return "{\"eventId\":\"" + ID + ":" + status + "\",\"type\":\"" + type + "\",\"transaction\":{\"id\":\"" + ID
                + "\",\"userId\":\"" + USER_ID + "\",\"accountId\":\"acc-1\",\"type\":\"" + transactionType
                + "\",\"amount\":10.50,\"occurredAt\":\"2026-11-10T12:00:00Z\",\"status\":\"" + status + "\"}}";
    }

    @Test
    void theTransactionIsCompletedAfterTheMovementIsSaved() {
        consumer.onMessage(event("TRANSACTION_PROCESSING", "DEPOSIT", "PROCESSING"));

        InOrder inOrder = inOrder(movementService, transactionService);
        inOrder.verify(movementService).record(USER_ID, ID, new BigDecimal("10.50"), MovementType.CREDIT);
        inOrder.verify(transactionService).complete(ID);
    }

    @Test
    void aProcessingWithdrawalRecordsADebit() {
        consumer.onMessage(event("TRANSACTION_PROCESSING", "WITHDRAWAL", "PROCESSING"));

        verify(movementService).record(USER_ID, ID, new BigDecimal("10.50"), MovementType.DEBIT);
    }

    @Test
    void aProcessingDepositRecordsACredit() {
        consumer.onMessage(event("TRANSACTION_PROCESSING", "DEPOSIT", "PROCESSING"));

        verify(movementService).record(USER_ID, ID, new BigDecimal("10.50"), MovementType.CREDIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSACTION_COMPLETED", "TRANSACTION_FAILED", "TRANSACTION_CREATED"})
    void otherEventsRecordNothing(String type) {
        consumer.onMessage(event(type, "DEPOSIT", "COMPLETED"));

        verifyNoInteractions(movementService, transactionService);
    }

    @Test
    void aDuplicateEventStillCompletesTheTransaction() {
        // The movement is already there, perhaps because the first delivery failed before completing the transaction
        when(movementService.record(any(), any(), any(), any())).thenThrow(new ConflictException("already has one"));

        assertDoesNotThrow(() -> consumer.onMessage(event("TRANSACTION_PROCESSING", "DEPOSIT", "PROCESSING")));

        verify(transactionService).complete(ID);
    }

    @Test
    void ifTheMovementCannotBeSavedTheTransactionIsNotCompletedAndTheFailureIsNotHidden() {
        when(movementService.record(any(), any(), any(), any())).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class,
                () -> consumer.onMessage(event("TRANSACTION_PROCESSING", "DEPOSIT", "PROCESSING")));

        verifyNoInteractions(transactionService);
    }

    @Test
    void ifTheTransactionCannotBeCompletedTheFailureIsNotHiddenSoThatItIsRetried() {
        when(transactionService.complete(ID)).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class,
                () -> consumer.onMessage(event("TRANSACTION_PROCESSING", "DEPOSIT", "PROCESSING")));
    }

    @Test
    void aMessageThatCannotBeReadIsInvalid() {
        assertThrows(InvalidMessageException.class, () -> consumer.onMessage("not json"));
        assertThrows(InvalidMessageException.class, () -> consumer.onMessage("{\"eventId\":\"x\"}"));
        verifyNoInteractions(movementService, transactionService);
    }

    @Test
    void aProcessingEventWithoutTheDataOfTheMovementIsInvalid() {
        String withoutAmount = "{\"eventId\":\"x\",\"type\":\"TRANSACTION_PROCESSING\",\"transaction\":{\"id\":\"" + ID
                + "\",\"userId\":\"" + USER_ID + "\",\"type\":\"DEPOSIT\"}}";

        assertThrows(InvalidMessageException.class, () -> consumer.onMessage(withoutAmount));
        verifyNoInteractions(movementService, transactionService);
    }
}
