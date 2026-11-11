# 0010. A notification context reads the transaction outcomes

- Status: Accepted
- Date: 2026-11-11

## Context

A transaction ends as `COMPLETED` or `FAILED` ([ADR 0008](0008-validate-transactions-in-a-consumer-of-created-events.md), [ADR 0009](0009-record-movements-in-a-consumer-of-the-update-events.md)), and each of these is published as an event on `payments-transactions-update` ([ADR 0006](0006-translate-changes-into-transaction-events.md)). The user should be told. A real system would send an e-mail or a push notification, but this is a study project, so the message is only logged.

Telling users is a different concern from processing transactions, and in a real system it would be a separate service with its own team and deployment.

## Decision

- **A new context, `notification`, with its own packages in every layer:** `domain.notification`, `application.notification`, `adapter.notification` and `adapter.messaging.notification`. It is separate from `transaction`, and does not use its classes.
- **It is fed by the events only.** `TransactionOutcomeConsumer` reads `payments-transactions-update` with its own consumer group (`transaction-notifier`), so it receives every event independently of the movement recorder (group `movement-recorder`). It reacts to `TRANSACTION_COMPLETED` and `TRANSACTION_FAILED` and ignores the rest.
- **The contract is the JSON, not the classes.** `TransactionOutcomeMessage` declares only the fields this context reads (the type, the transaction id and the user id) and ignores the others, and the type is text, so an event type it does not know is ignored and not an error. If the context became a separate service, nothing would have to change.
- **How the user is told is a port.** `NotificationService.notifyOutcome` builds a `Notification` and calls the `Notifier` port. The only implementation, `LoggingNotifier`, writes a log line. An e-mail or push implementation replaces it without touching the rest.
- **Failures** follow the usual policy: retried 3 times, 1 second apart, then `payments-transactions-update-dlq`. A message that cannot be understood goes there without retries. The topic already had that dead letter queue, so nothing was added.

Alternatives considered:

- **Notify from the movement recorder, after completing the transaction.** It needs no new consumer, but it ties the notification to one step of the transaction context, cannot tell the user about a `FAILED` transaction (those never reach the recorder), and would have to move when the notification becomes its own service.
- **Reuse `TransactionEventMessage` and `TransactionEventType`.** Less code, but the notification context would depend on the transaction context, which is what the separation is meant to avoid.
- **Put the notification in the `transaction` packages.** The simplest, but it would hide that this is a different concern, and it would be the part to untangle later.

## Consequences

- Delivery is at least once, so a user can be told twice about the same event. A real notifier would use the event id to avoid it. The log line has no such protection.
- The notification is independent of the transaction: if it fails, the transaction is not affected, and if the notifier is slow, only this consumer falls behind.
- A message in the dead letter queue is a notification that was never sent, and nothing replays it.
- A `FAILED` transaction records no reason, so the notification cannot give one either.
- Two groups read the same topic, so every update event is read twice.
