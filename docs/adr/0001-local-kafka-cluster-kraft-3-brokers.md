# 0001. Local Kafka cluster with 3 brokers in KRaft mode

- Status: Accepted
- Date: 2026-11-05

## Context

The project exercises distributed-systems scenarios (idempotency, at-least-once delivery, partitioning, failover, graceful degradation) and needs a local Kafka that behaves like a real cluster.

## Decision

Use 3 Kafka brokers in KRaft mode (Kafka Raft, no ZooKeeper), with replication factor 3 and `min.insync.replicas=2`, plus Schema Registry and Kafka UI, defined in `docker/docker-compose.yml`. Topics are created by a `kafka-init` job and automatic topic creation is disabled.

## Consequences

- One broker can be taken down without losing availability or writes with `acks=all`, which allows testing failover.
- It uses more memory than a single broker.
- Without volumes, `down` deletes the data. This is acceptable for development.
- No authentication or TLS, so it is not suitable for any environment other than local.
