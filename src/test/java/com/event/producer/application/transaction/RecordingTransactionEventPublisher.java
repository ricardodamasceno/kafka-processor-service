package com.event.producer.application.transaction;

import com.event.producer.application.shared.PublishFailedException;
import com.event.producer.domain.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;

/** Test double for the publisher port: remembers what was published, and can simulate a broker failure. */
class RecordingTransactionEventPublisher implements TransactionEventPublisher {

    final List<Transaction> published = new ArrayList<>();
    boolean failing;

    @Override
    public void publishCreationRequested(Transaction transaction) {
        if (failing) {
            throw new PublishFailedException("broker unavailable", new RuntimeException());
        }
        published.add(transaction);
    }
}
