# Local Kafka cluster

Setup in `docker/` that starts a local development Kafka cluster and creates the project topics. The same Compose file also starts PostgreSQL (see [database.md](database.md)).

## Files

| File | Purpose |
|---|---|
| `docker/docker-compose.yml` | Defines brokers, Schema Registry, Kafka UI, the topic creation job and PostgreSQL |
| `docker/create-topics.sh` | Idempotent script that creates the topics (`--if-not-exists`) |

## Services

| Service | Image | Host port | Notes |
|---|---|---|---|
| `kafka-1` | `apache/kafka:3.9.0` | 19092 | Broker + controller (KRaft), with healthcheck |
| `kafka-2` | `apache/kafka:3.9.0` | 29092 | Broker + controller (KRaft) |
| `kafka-3` | `apache/kafka:3.9.0` | 39092 | Broker + controller (KRaft) |
| `schema-registry` | `confluentinc/cp-schema-registry:7.7.1` | 8082 | Waits for `kafka-1` to be healthy |
| `kafka-ui` | `provectuslabs/kafka-ui:latest` | 8081 | Web interface |
| `kafka-init` | `apache/kafka:3.9.0` | n/a | Runs `create-topics.sh` and exits (code 0) |
| `postgres` | `postgres:17` | 5432 | See [database.md](database.md) |

The Compose project is named `events-processor`. Port 8080 is intentionally left free for the application service.

## Usage

From the project root:

```bash
docker compose -f docker/docker-compose.yml up -d
```

Stop and remove the containers (Kafka data is lost, since there are no volumes for it):

```bash
docker compose -f docker/docker-compose.yml down
```

Check the status, including `kafka-init`, which has already finished:

```bash
docker compose -f docker/docker-compose.yml ps -a
```

## Connection

- From an application running on the host: `localhost:19092,localhost:29092,localhost:39092`.
- From another container on the same Compose network: `kafka-1:9092,kafka-2:9092,kafka-3:9092`.
- Schema Registry: http://localhost:8082.
- Kafka UI: http://localhost:8081.

Each broker has two listeners: `INTERNAL` (port 9092, between containers) and `EXTERNAL` (port 19092/29092/39092, for the host). The `CONTROLLER` listener (9093) is used only by the KRaft quorum.

## Cluster configuration

| Parameter | Value | Reason |
|---|---|---|
| Mode | KRaft, 3 nodes with broker and controller roles | No ZooKeeper; the quorum tolerates 1 failure |
| `default.replication.factor` | 3 | Every partition exists on all 3 brokers |
| `min.insync.replicas` | 2 | With `acks=all`, a write needs 2 replicas; tolerates 1 broker down |
| `offsets.topic.replication.factor` | 3 | Consumer group offsets are replicated |
| `transaction.state.log.replication.factor` / `min.isr` | 3 / 2 | Supports transactional producers |
| `num.partitions` | 3 | Default, used only if a topic does not define it |
| `auto.create.topics.enable` | false | Topics are created deliberately by the script |
| `group.initial.rebalance.delay.ms` | 0 | Consumer groups start without delay in dev |

## Topics

Defined in the `TOPICS` variable of `docker/create-topics.sh`, in the format `name:partitions:replication`.

| Topic | Partitions | Replication | Planned use |
|---|---|---|---|
| `payments.transfers` | 6 | 3 | Transfer commands/events |
| `payments.transfers.dlq` | 3 | 3 | Messages that failed after retries (dead letter queue) |
| `ledger.events` | 6 | 3 | Immutable ledger events |
| `ad.clicks` | 6 | 3 | Raw clicks for aggregation |
| `ad.clicks.aggregated` | 3 | 3 | Counts per window |

To add a topic, add a line to `TOPICS` and run again:

```bash
docker compose -f docker/docker-compose.yml up kafka-init
```

Alternatively, create one manually:

```bash
docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:9092 --create --topic my.topic --partitions 6 --replication-factor 3
```

## Failure experiments

Stop and restore a broker:

```bash
docker compose -f docker/docker-compose.yml stop kafka-2
```
```bash
docker compose -f docker/docker-compose.yml start kafka-2
```

See leaders and ISR (in-sync replicas) of a topic:

```bash
docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:9092 --describe --topic payments.transfers
```

With 1 broker stopped, `acks=all` writes keep working (2 replicas in sync). With 2 stopped, they fail with `NotEnoughReplicas`, and the KRaft quorum also loses its majority. A step-by-step guide is in [kafka-experiments.md](kafka-experiments.md).

## Known limitations

- No volumes for Kafka: `down` deletes all its data.
- No authentication or TLS (PLAINTEXT), for local use only.
- The `provectuslabs/kafka-ui:latest` image is not version-pinned.
- Topic names that mix a dot and an underscore can collide in metrics, and Kafka warns about this when topics are created. Avoid mixing both separators in names.
