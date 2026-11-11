# Change data capture with Debezium

Debezium reads the row changes of selected tables from the PostgreSQL write-ahead log (WAL) and publishes each change to a topic of that table. The application does not call Kafka for these events: it only writes to the database. The reasons are in [ADR 0004](adr/0004-cdc-per-table-with-debezium.md), which replaces [ADR 0003](adr/0003-outbox-with-debezium-cdc.md) (outbox).

## Current state

The infrastructure is ready: PostgreSQL with logical decoding, Kafka Connect with Debezium, the connector configuration, and the `transactions-cdc-events` topic. The `transactions` table is the only table captured, and migration V1 sets `REPLICA IDENTITY FULL` on it (it takes effect when the migration is applied).

**The application uses it** (see [ADR 0005](adr/0005-pending-transactions-and-cdc-events.md)). `POST /transactions` saves the transaction as `PENDING` (the `status` column is part of migration V1) and does not call Kafka. Debezium publishes the change to `transactions-cdc-events`, and a translator reads it. The translator is the **only** reader of that topic: it turns each change into a transaction event and publishes it to `payments-transactions-create` or `payments-transactions-update` ([ADR 0006](adr/0006-translate-changes-into-transaction-events.md)). Every other consumer reads those events and never sees `before` and `after`. All the code that knows Debezium's format is in the package `adapter.messaging.cdc`. The validation consumer reads the creation events and moves a transaction to `PROCESSING` or `FAILED` ([ADR 0008](adr/0008-validate-transactions-in-a-consumer-of-created-events.md)); that status change goes through this same path. Carrying out a `PROCESSING` transaction and notifying the final state are the next steps.

## Convention

A table that uses CDC has all of these, and a table that is not listed produces nothing:

| Rule | Where |
|---|---|
| The table is listed in `table.include.list` | `docker/debezium/cdc-connector.json` |
| It has a topic named `<table>-cdc-events`, and a `<table>-cdc-events-dlq` for its consumers | `docker/create-topics.sh` |
| Its migration sets `ALTER TABLE <table> REPLICA IDENTITY FULL;` when consumers need the old row | `src/main/resources/db/migration` |

The topic name comes from the table name (a `RegexRouter` turns `events-processor.public.transactions` into `transactions-cdc-events`). A table with an underscore keeps it (`account_restrictions` becomes `account_restrictions-cdc-events`), so decide how to name such tables before capturing the first one.

## Pieces

| Piece | Where | Role |
|---|---|---|
| PostgreSQL with `wal_level=logical` | `postgres` service in `docker/docker-compose.yml` | Exposes the WAL in a form Debezium can read |
| Kafka Connect with Debezium | `kafka-connect` service (port 8083) | Runs the connector. The same process reads the WAL and publishes to Kafka |
| Connector configuration | `docker/debezium/cdc-connector.json` | Which tables to capture and how to name the topics |
| Registration script | `docker/register-connector.sh` | Creates or updates the connector through the Connect REST API |
| Internal topics | `connect-configs`, `connect-offsets`, `connect-statuses` | Connect's own state. `connect-offsets` records how far the WAL was read. Created by Connect (1 partition, replication factor 3) |

## How it works

```
application ── INSERT / UPDATE / DELETE ──> transactions                  (PostgreSQL)
                                                │
                                      the WAL records the change
                                                │
                          Kafka Connect + Debezium read it
                                                │
                                                ▼
                                   transactions-cdc-events
```

Only committed transactions appear in the WAL, so a change that was rolled back is never published. This also covers changes that do not come from the application code, such as `PUT` and `DELETE` requests or a manual SQL statement.

## Message format

The **key** is the primary key of the row, as JSON: `{"id":"3f2b8f36-0000-4000-8000-000000000001"}`. Changes to the same row go to the same partition and keep their order.

The **value** is Debezium's envelope, as plain JSON. This is an update of the amount (`source` and the timestamps are left out):

```json
{
  "op": "u",
  "before": {"id": "3f2b8f36-0000-4000-8000-000000000001", "account_id": "acc-1", "type": "DEPOSIT", "amount": "10.50", "occurred_at": "2026-11-10T12:00:00.000000Z"},
  "after":  {"id": "3f2b8f36-0000-4000-8000-000000000001", "account_id": "acc-1", "type": "DEPOSIT", "amount": "25.00", "occurred_at": "2026-11-10T12:00:00.000000Z"}
}
```

| `op` | Change | `before` | `after` |
|---|---|---|---|
| `c` | Insert | `null` | the new row |
| `u` | Update | the old row (needs `REPLICA IDENTITY FULL`) | the new row |
| `d` | Delete | the deleted row (needs `REPLICA IDENTITY FULL`) | `null` |

- `amount` is a **string** (`decimal.handling.mode=string`), which keeps the exact scale of money. Parse it as `BigDecimal`.
- Timestamps are ISO 8601 text in UTC. UUIDs are text.
- Deleting a row does not produce a tombstone (`tombstones.on.delete=false`), because the topic is not compacted.

## REPLICA IDENTITY FULL

By default PostgreSQL writes only the primary key of the old row to the WAL on `UPDATE` and `DELETE`. Checked against a scratch table with each setting:

| | `DEFAULT` | `FULL` |
|---|---|---|
| `before` of an update | `null` | the complete old row |
| `before` of a delete | only the key is real; the other columns show placeholder values (`""`, `0.00`, `1970-01-01`) | the complete deleted row |

The placeholder values under `DEFAULT` look like real data, which is the main reason to use `FULL` on any table whose deletes or updates matter. The cost is a larger WAL: the whole old row is written on every update and delete. It is chosen per table, so insert-only tables do not need it. A table without a primary key needs it to replicate updates and deletes at all.

## Start and register

The Connect container starts with the rest of the environment:

```bash
docker compose -f docker/docker-compose.yml up -d
```

Apply the migrations first. The connector creates a publication on the captured tables, so registering before they exist fails:

```bash
./gradlew flywayMigrate
```

Register the connector. The script is idempotent: running it again updates the configuration.

```bash
sh docker/register-connector.sh
```

## Checking it

State of the connector and its task (both should be `RUNNING`):

```bash
curl -s http://localhost:8083/connectors/cdc-connector/status
```

The same information is in Kafka UI (http://localhost:8081), under Kafka Connect.

Read the changes of `transactions`:

```bash
docker exec kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka-1:9092 --topic transactions-cdc-events --from-beginning --property print.key=true
```

How much WAL PostgreSQL is holding for the connector (it should stay small):

```bash
docker exec postgres psql -U events -d events_processor -c "SELECT slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS retained_wal FROM pg_replication_slots;"
```

## Adding a table to CDC

1. Add `ALTER TABLE <table> REPLICA IDENTITY FULL;` to a migration (if consumers need the old row) and apply it.
2. Add `<table>-cdc-events:6:3` and `<table>-cdc-events-dlq:3:3` to `docker/create-topics.sh`, then run `docker compose -f docker/docker-compose.yml up kafka-init`.
3. Add `public.<table>` to `table.include.list` in `docker/debezium/cdc-connector.json` (comma separated) and run `sh docker/register-connector.sh`.
4. Change a row and read the new topic to confirm it receives the change.

Topics are not created automatically, so the topic must exist before the first change is captured.

## Behavior to know

- **One connector captures all the listed tables.** They share one replication slot, so the WAL is read once.
- **Only listed tables are captured.** A write to any other table produces no message (checked with a table outside the list).
- **Rows that existed before the first registration are not published** (`snapshot.mode` is `no_data`). The connector starts from the moment it first connects. Use `initial` if a new consumer needs the current rows too.
- **Changes made while the connector is down or paused are published when it returns.** PostgreSQL keeps the position in the replication slot, so nothing is lost. This was checked by pausing the connector, writing a row and resuming it.
- **Delivery is at-least-once.** After a crash the connector can publish a change twice, so consumers must be idempotent.
- **A stopped connector makes PostgreSQL retain WAL.** If Connect stays down for a long time the disk can fill up. Watch the retained WAL with the query above.
- **Every column of a captured table goes to Kafka.** For sensitive columns use `column.exclude.list` in the connector configuration.
- **Adding a column to a captured table needs no change to the connector.** It keeps running, and the new column appears in the messages from then on (checked by adding `user_id` while the connector was running). Messages published before the change do not have it. The translator ignores the columns it does not read, but it **does read `user_id`**: a change published before that column existed is rejected and goes to the dead letter queue if it is ever read again (for example by a new consumer group that starts from the beginning).
- **Changing the configuration:** edit `cdc-connector.json` and run the registration script again.
- **Removing the connector:** deleting it does not drop the replication slot. Drop it by hand, otherwise PostgreSQL keeps retaining WAL:

```bash
curl -s -X DELETE http://localhost:8083/connectors/cdc-connector
```
```bash
docker exec postgres psql -U events -d events_processor -c "SELECT pg_drop_replication_slot('cdc_slot');"
```

## Known limitations

- The database schema of a captured table is also the contract of its topic. Renaming or retyping a column changes the messages that consumers read.
- The topic carries row changes, not business events. The meaning comes from the operation and the data (for example `u` with `before.status = PENDING` and `after.status = COMPLETED`), and only the translator interprets it.
- The message key is the primary key, so order is kept per row, not per account. Ordering by account would need a key transformation.
- The credentials in `cdc-connector.json` are the local defaults. For another environment use Kafka Connect's config providers instead of plain text.
- One Connect worker runs locally. In production Connect runs as a cluster (several workers with the same `GROUP_ID`), so another worker takes over when one fails.
