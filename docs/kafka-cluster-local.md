# Local Kafka cluster

Setup in `docker/` that starts a local development Kafka cluster and creates the project topics. The same Compose file also starts PostgreSQL (see [database.md](database.md)) , Kafka Connect with Debezium (see [debezium.md](debezium.md)) and Redis (see [redis.md](redis.md)).

## Files

| File | Purpose |
|---|---|
| `docker/docker-compose.yml` | Defines brokers, Schema Registry, Kafka UI, the topic creation job, PostgreSQL, Kafka Connect and Redis |
| `docker/create-topics.sh` | Idempotent script that creates the topics (`--if-not-exists`) |
| `docker/debezium/cdc-connector.json` | Configuration of the Debezium connector: which tables are captured and how their topics are named |
| `docker/register-connector.sh` | Registers (or updates) that connector in Kafka Connect |

## Services

| Service | Image | Host port | Notes |
|---|---|---|---|
| `kafka-1` | `apache/kafka:3.9.0` | 19092 | Broker + controller (KRaft), with healthcheck |
| `kafka-2` | `apache/kafka:3.9.0` | 29092 | Broker + controller (KRaft) |
| `kafka-3` | `apache/kafka:3.9.0` | 39092 | Broker + controller (KRaft) |
| `schema-registry` | `confluentinc/cp-schema-registry:7.7.1` | 8082 | Waits for `kafka-1` to be healthy |
| `kafka-ui` | `provectuslabs/kafka-ui:latest` | 8081 | Web interface |
| `kafka-init` | `apache/kafka:3.9.0` | n/a | Runs `create-topics.sh` and exits (code 0) |
| `postgres` | `postgres:17` | 5432 | Runs with `wal_level=logical`. See [database.md](database.md) |
| `kafka-connect` | `quay.io/debezium/connect:3.7.1.Final` | 8083 | Kafka Connect with the Debezium plugins. Waits for `kafka-init` and `postgres`. See [debezium.md](debezium.md) |
| `redis` | `redis:7.4-alpine` | 6379 | In-memory key-value store, with a health check. See [redis.md](redis.md) |
| `redisinsight` | `redis/redisinsight:3.8.0` | 5540 | Web interface for Redis. Waits for `redis` to be healthy. See [redis.md](redis.md) |

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
- Kafka Connect REST API: http://localhost:8083.
- Redis: `localhost:6379`.
- RedisInsight (Redis web interface): http://localhost:5540.

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
| `transactions-cdc-events` | 6 | 3 | Row changes of the `transactions` table, published by Debezium (key = primary key). Internal: only the translator (group `transaction-event-translator`) reads it. See [debezium.md](debezium.md) |
| `transactions-cdc-events-dlq` | 3 | 3 | Messages from the CDC topic that failed after retries (dead letter queue) |
| `movements-cdc-events` | 6 | 3 | Row changes of the `movements` table, published by Debezium (key = primary key). No consumer yet |
| `movements-cdc-events-dlq` | 3 | 3 | Dead letter queue of the topic above, for its future consumer |
| `payments-transactions-create` | 6 | 3 | `TRANSACTION_CREATED` events, the work to do (key = transaction id). Published by the translator and read by the validation consumer (group `transaction-validator`) |
| `payments-transactions-create-dlq` | 3 | 3 | Dead letter queue of the topic above: what the validation consumer could not process after its retries, or could not understand |
| `payments-transactions-update` | 6 | 3 | `TRANSACTION_PROCESSING`, `TRANSACTION_COMPLETED` and `TRANSACTION_FAILED` events, facts about what happened (key = transaction id). Published by the translator and read by two consumers with their own groups: the movement recorder (`movement-recorder`) and the notifier of the notification context (`transaction-notifier`) |
| `payments-transactions-update-dlq` | 3 | 3 | Dead letter queue of the topic above: what the movement recorder or the notifier could not process after their retries, or could not understand |

Topic names use hyphens as the only separator, with `-dlq` added for the dead letter queue of a topic. The topic of a table captured by change data capture is `<table>-cdc-events`.

Kafka Connect creates three more topics by itself, for its own state: `connect-configs`, `connect-offsets` and `connect-statuses` (1 partition, replication factor 3). They are not in `create-topics.sh`.

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
docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:9092 --describe --topic transactions-cdc-events
```

With 1 broker stopped, `acks=all` writes keep working (2 replicas in sync). With 2 stopped, they fail with `NotEnoughReplicas`, and the KRaft quorum also loses its majority. A step-by-step guide is in [kafka-experiments.md](kafka-experiments.md).

## Known limitations

- No volumes for Kafka: `down` deletes all its data.
- No authentication or TLS (PLAINTEXT), for local use only.
- The `provectuslabs/kafka-ui:latest` image is not version-pinned.
- Topic names that mix a dot and an underscore can collide in metrics, and Kafka warns about this when topics are created. The project avoids it by using hyphens as the only separator.
- In `kafka-topics.sh`, the `--topic` value of `--describe`, `--alter` and `--delete` is a **regular expression** that has to match the whole topic name. A dot in it matches any character, so `--delete --topic payments.transactions` also deletes `payments-transactions`. Hyphens have no special meaning, but escape the dots (`payments\.transactions`) if a name ever has them. To match several topics, use `.*`, for example `'^transactions-.*'`.
