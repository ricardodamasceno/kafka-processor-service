# 0004. Change data capture per table with Debezium

- Status: Accepted
- Date: 2026-11-10

## Context

[ADR 0003](0003-outbox-with-debezium-cdc.md) solved the gap between saving to the database and publishing to Kafka with a transactional outbox: the application writes an event row next to the business change, and Debezium publishes it. That keeps the event contract independent of the tables, but it costs an extra table, an extra write for every event, and an event contract to design and maintain. The consumers here live in the same service and the same repository, and several of them only need to know that a row changed, so that independence has little value for this project.

## Decision

Capture the **row changes of selected tables** and publish each change to a topic of that table.

- **One topic per captured table, named `<table>-cdc-events`**, with a dead letter queue `<table>-cdc-events-dlq` for its consumers. For example `transactions` publishes to `transactions-cdc-events`.
- **Capture is opt-in.** Only the tables listed in `table.include.list` of the connector are captured, and a table that is not listed produces nothing.
- **A single connector** captures all the listed tables, with one replication slot. A `RegexRouter` names the topics from the table names.
- **`REPLICA IDENTITY FULL` on the tables whose consumers need the old row**, set in the migration of the table. By default PostgreSQL logs only the key of the old row, and the `before` image of a delete then carries placeholder values that look like real data.
- **Message format:** the key is the primary key as JSON, and the value is Debezium's envelope (`before`, `after`, `op`) as plain JSON. Decimals are sent as strings to keep the exact scale of money.
- The application writes only to the database. It does not call Kafka for these events.

Alternatives considered:

- **Transactional outbox (ADR 0003).** The contract of the event is independent of the schema, and the event can state a business fact directly. It needs an extra table and write per event, and it does not capture changes that do not go through the code. It is a better fit when consumers belong to other teams or the contract must stay stable. This ADR supersedes it.
- **Publishing directly after saving.** It leaves the gap described in ADR 0003.
- **A job that polls the table.** It adds latency, queries the database all the time, and does not keep the order per key when several instances run.

## Consequences

- A change is published if and only if its transaction committed, and it is published in commit order. A broker outage delays publishing but loses nothing.
- Changes that do not come from the application code (a `PUT`, a `DELETE`, a manual statement) are captured too.
- **The table schema becomes the contract of its topic.** Renaming or retyping a column affects every consumer, so such changes need care.
- Consumers receive row changes and must derive the business meaning from the operation and the data. A change in `status` from `PENDING` to `COMPLETED` is the "payment completed" event.
- Every column of a captured table is published. Sensitive columns must be excluded or masked in the connector.
- `REPLICA IDENTITY FULL` makes the WAL larger, so it is set only where the old row is needed.
- Delivery is at-least-once, so every consumer must tolerate duplicates.
- The key is the primary key, so ordering is per row. Ordering per account needs a key transformation.
- The flow of ADR 0002 changes: the API inserts the row itself (with a status) instead of publishing an event for the consumer to insert. This is done in [ADR 0005](0005-pending-transactions-and-cdc-events.md).
- The topics `payments-transactions-create` and `payments-transactions-update` (and their DLQs) overlapped with `transactions-cdc-events`, so they were removed. [ADR 0006](0006-translate-changes-into-transaction-events.md) brings them back with a different role, to carry translated transaction events.
- New components to operate: Kafka Connect and the connector. A stopped connector makes PostgreSQL retain WAL, so the retained size of the replication slot must be monitored.
- PostgreSQL must run with `wal_level=logical`.
- There is no outbox table to clean up.
- The setup is described in [debezium.md](../debezium.md).
