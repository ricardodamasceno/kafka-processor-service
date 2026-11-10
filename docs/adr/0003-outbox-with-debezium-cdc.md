# 0003. Outbox with change data capture (Debezium)

- Status: Superseded by [0004](0004-cdc-per-table-with-debezium.md)
- Date: 2026-11-10

## Context

A request that changes the database and must also announce an event to Kafka writes to two systems that have no common transaction. Saving and then sending leaves a gap: if the process stops between the two steps, or Kafka is unavailable after the commit, the data exists and the event never leaves. The code can catch an exception, but it cannot catch a process that disappears, and a commit that already happened cannot be undone.

## Decision

Use the **transactional outbox** pattern, published by **change data capture (CDC) with Debezium**.

- The application writes the event as a row in `outbox_events`, in the same database transaction as the business change. Both are saved, or neither is.
- Debezium, running in Kafka Connect, reads the insert from the PostgreSQL write-ahead log and publishes it to the topic named in the row. The key is `aggregate_id` and the value is the `payload`.
- The application no longer calls Kafka for these events.

Alternatives considered:

- **Save, then send (with a try/catch).** Simple, but it leaves the gap described above.
- **A scheduled job that polls the outbox** (with `SELECT ... FOR UPDATE SKIP LOCKED` so several instances do not take the same rows). It needs no extra infrastructure, but it adds latency equal to the polling interval, queries the database all the time, and does not keep the order per key when several instances run.
- **Kafka transactions.** They make "consume and produce" atomic inside Kafka, but they do not include the database.

## Consequences

- An event is published if and only if its transaction committed. A broker outage delays publishing but loses nothing.
- Events reach Kafka within milliseconds of the commit, in commit order, so the order per key is kept.
- New components to operate: Kafka Connect and the connector. A stopped connector makes PostgreSQL retain WAL, so the retained size of the replication slot must be monitored.
- Delivery is at-least-once, so every consumer must tolerate duplicates.
- `outbox_events` only grows. A cleanup job is needed.
- PostgreSQL must run with `wal_level=logical`.
- The setup is in place (see [debezium.md](../debezium.md)), but the application does not use the outbox yet.
