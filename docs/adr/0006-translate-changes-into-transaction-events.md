# 0006. Translate row changes into transaction events

- Status: Accepted
- Date: 2026-11-10

## Context

[ADR 0004](0004-cdc-per-table-with-debezium.md) publishes the row changes of the `transactions` table, and [ADR 0005](0005-pending-transactions-and-cdc-events.md) lets consumers read them. That makes the table schema and Debezium's format the contract of the topic. Every consumer would have to understand `before` and `after` to find out what happened, which is not its job, and replacing the capture strategy would change every consumer.

## Decision

**One translator is the only reader of `transactions-cdc-events`.** It turns each row change into a transaction event and publishes it. Every other consumer reads the transaction events.

- The translator is `TransactionChangeConsumer` plus `TransactionChangeTranslator`, in the package `adapter.messaging.cdc`. That package is the only place that knows Debezium's format and the columns of the table. The events are defined in the application layer (`TransactionEvent`, `TransactionEventType`) and published through the port `TransactionEventPublisher`, which the Kafka adapter implements.
- **Two topics, each with a dead letter queue:**
  - `payments-transactions-create` carries `TRANSACTION_CREATED`. It is the **work to do**, and the executor will read it.
  - `payments-transactions-update` carries `TRANSACTION_PROCESSING`, `TRANSACTION_COMPLETED` and `TRANSACTION_FAILED`. They are **facts about what happened**, and the notifier will read them.
- **The key is the transaction id** in both topics, so the events of one transaction keep their order inside a topic.
- **An event carries the complete state of the transaction**, a type, and an event id (`<transactionId>:<status>`). A consumer never needs the previous state. A transaction reaches each status once, so the event id is the same if the event is published twice, and a consumer can use it to ignore a duplicate.
- **Translation rules:**

| Row change | Event |
|---|---|
| Insert with status `PENDING` | `TRANSACTION_CREATED` |
| Update that changed the status to `PROCESSING`, `COMPLETED` or `FAILED` | The event of that status |
| Any other insert or update (for example a changed amount), a delete, a snapshot read | None |

- An update that arrives without the old row means the table lost `REPLICA IDENTITY FULL`. The translator rejects it, so it goes to the dead letter queue instead of silently dropping a status change.
- `transactions-cdc-events` becomes an internal topic. The transaction event topics are the contract that other consumers rely on.
- Publishing waits for the broker acknowledgement. If it fails, the offset is not committed, the message is retried 3 times and then goes to `transactions-cdc-events-dlq`.

Alternatives considered:

- **Consumers read the change events directly** (ADR 0005). It needs no translator, but every consumer depends on the table schema and on `before`/`after`.
- **Translate inside Debezium** with a transformation. The rules would live in the connector configuration, where they are hard to test and to evolve.
- **Kafka Streams.** The translation keeps no state, so a plain consumer and producer are enough. Streams fits better where there is state, such as fraud detection.
- **One topic with the event type in the payload**, as ADR 0002 recommended. It would keep every event of a transaction in order. Two topics were chosen because creating is work to do and the rest are facts, and each consumer reads only what it needs.

## Consequences

- Replacing the capture strategy (an outbox, another tool) changes only the package `adapter.messaging.cdc`.
- Consumers react to "transaction completed" instead of reading a row change.
- There is one more hop on the path, and one more component that can fail. If the translator stops, the change events stay in `transactions-cdc-events` and it continues from where it stopped, so its consumer lag must be monitored.
- Delivery is at-least-once: the same event can be published twice. Kafka transactions are not used, because duplicates are harmless when consumers use the event id.
- Order is kept inside each topic but not between the two. A consumer that reads both can see an update before the creation, which is why each event carries the complete state.
- Changes that do not alter the status (a `PUT`) and deletes produce no event, so consumers never see them. Whether they should is still to be decided.
- Nothing reads the two event topics yet. The executor and the notifier are the next steps.
- The event format is now a public contract. It should get a versioned schema (Schema Registry) before more consumers depend on it.
