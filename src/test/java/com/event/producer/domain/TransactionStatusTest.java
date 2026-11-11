package com.event.producer.domain;

import com.event.producer.domain.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.event.producer.domain.transaction.TransactionStatus.COMPLETED;
import static com.event.producer.domain.transaction.TransactionStatus.FAILED;
import static com.event.producer.domain.transaction.TransactionStatus.PENDING;
import static com.event.producer.domain.transaction.TransactionStatus.PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionStatusTest {

    /** Every allowed move. Anything not listed here must be refused. */
    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(PROCESSING, FAILED),
            PROCESSING, EnumSet.of(COMPLETED, FAILED),
            COMPLETED, EnumSet.noneOf(TransactionStatus.class),
            FAILED, EnumSet.noneOf(TransactionStatus.class));

    @Test
    void everyMoveIsAllowedOrRefusedAsDescribed() {
        for (TransactionStatus from : TransactionStatus.values()) {
            for (TransactionStatus to : TransactionStatus.values()) {
                assertEquals(ALLOWED.get(from).contains(to), from.canMoveTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void aTransactionNeverStaysInTheSameStatusByMoving() {
        for (TransactionStatus status : TransactionStatus.values()) {
            assertEquals(false, status.canMoveTo(status), status + " -> " + status);
        }
    }

    @Test
    void completedAndFailedAreFinal() {
        for (TransactionStatus to : TransactionStatus.values()) {
            assertEquals(false, COMPLETED.canMoveTo(to));
            assertEquals(false, FAILED.canMoveTo(to));
        }
    }
}
