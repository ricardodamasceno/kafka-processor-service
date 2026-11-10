# Documentation

Convention: a `docs/` folder at the project root, with architecture decisions recorded as ADRs (Architecture Decision Records, Michael Nygard format) in `docs/adr/`.

## Index

- [Architecture](architecture.md): Clean Architecture layers, packages and request flow.
- [Local Kafka cluster](kafka-cluster-local.md): how to start and use the environment in `docker/`.
- [Database and migrations](database.md): local PostgreSQL and Flyway (manual migrations).
- [Change data capture with Debezium](debezium.md): Kafka Connect and Debezium publishing the changes of selected tables to `<table>-cdc-events` topics.
- [Kafka cluster experiments](kafka-experiments.md): hands-on guide covering keys, consumer groups, failures, `acks` and replay.

## Architecture decisions (ADRs)

- [0001. Local Kafka cluster with 3 brokers in KRaft mode](adr/0001-local-kafka-cluster-kraft-3-brokers.md)
- [0002. Asynchronous creation with Kafka](adr/0002-asynchronous-creation-with-kafka.md) (superseded by 0005)
- [0003. Outbox with change data capture (Debezium)](adr/0003-outbox-with-debezium-cdc.md) (superseded by 0004)
- [0004. Change data capture per table with Debezium](adr/0004-cdc-per-table-with-debezium.md)
- [0005. Create transactions as PENDING and react to their change events](adr/0005-pending-transactions-and-cdc-events.md)
- [0006. Translate row changes into transaction events](adr/0006-translate-changes-into-transaction-events.md)

## Rules

- ADRs are numbered sequentially (`NNNN-title-in-kebab-case.md`) and are never deleted. A superseded decision gets the status `Superseded by NNNN`.
- Each ADR has: Status, Context, Decision and Consequences.
- Usage guides (how to run, how to operate) live in the root of `docs/`, in files named after their subject.
- Everything in the project (documentation, code comments, scripts, file names) is written in English.
