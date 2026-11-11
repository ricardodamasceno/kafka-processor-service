# 0005. Create transactions as PENDING and react to their change events

- Status: Accepted
- Date: 2026-11-10

## Context

[ADR 0002](0002-asynchronous-creation-with-kafka.md) made creation asynchronous by publishing an event from the request and inserting the row in a consumer. [ADR 0004](0004-cdc-per-table-with-debezium.md) captures the changes of selected tables with Debezium, so the application no longer needs to publish an event: the row itself is the event.

## Decision

- `POST /transactions` validates the request, assigns an id and **saves the transaction with the status `PENDING`**. It then answers `202 Accepted` with an empty body. The application does not call Kafka.
- Debezium publishes the change to `transactions-cdc-events`. A translator reads it and publishes transaction events, and the other consumers read those events instead of the change ([ADR 0006](0006-translate-changes-into-transaction-events.md)). A reaction, such as executing a transaction when a `PENDING` row is inserted, belongs to those consumers.
- The `transactions` table has a `status` column, defined in its creation migration (V1): `PENDING`, `PROCESSING`, `COMPLETED` or `FAILED`. There is no default, so every insert states its status.
- `PUT` keeps the current status, because the request does not carry one. `GET` returns it.
- The `202` with an empty body stays, because processing is still asynchronous.

Kept from ADR 0002:

- Delivery is at-least-once, so a consumer must tolerate duplicates.
- The offset is committed after each message is processed.
- A failure is retried 3 times, 1 second apart, then the message goes to the dead letter queue (`transactions-cdc-events-dlq`). Malformed payloads and broken domain rules go to the DLQ without retries, because retrying cannot fix them.
- Messages are plain JSON strings, read into a message class that is separate from the domain class.

## Consequences

- A transaction can be read right after the `202`, as `PENDING`. The window in which `GET`, `PUT` and `DELETE` returned 404 is gone.
- Creating a transaction depends only on the database. A Kafka or Debezium outage delays the processing but does not stop requests from being accepted, so the `503` for a broker failure no longer exists.
- At first nothing moved a transaction past `PENDING`, and there was no state machine. [ADR 0008](0008-validate-transactions-in-a-consumer-of-created-events.md) adds the validation step, which moves it to `PROCESSING` or `FAILED`, and the allowed moves in `TransactionStatus.canMoveTo`. Carrying out a `PROCESSING` transaction is still not implemented.
- There is no idempotency key from the client. A client that retries a `POST` after a timeout creates a second transaction. A client-supplied id or key with a unique index is the next protection to add.
- The response still has no id. Now that the row exists, the id could be returned.
- The consumer sees every change to the table, including `PUT` and `DELETE`, not only the creation.
- The topics `payments-transactions-create` and `payments-transactions-update`, and their DLQs, were removed when the application stopped using them. ADR 0006 brings them back with a different role: they carry the translated transaction events.
