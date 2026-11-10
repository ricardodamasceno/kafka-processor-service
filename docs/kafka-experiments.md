# Kafka cluster experiments

A hands-on guide to observe how Kafka behaves: keys and partitions, consumer groups, broker failures, write guarantees and offset replay.

**How to use it:** before each step, write down in the log table what you **predict** will happen. Then run it and record the **result**. The results that surprise you are the ones that teach the most.

## Prerequisites

- Cluster running (`docker compose -f docker/docker-compose.yml up -d`).
- Kafka UI (web interface) at http://localhost:8081.
- A shortcut for the commands, so you do not repeat the path (run it in the terminal where you run the experiments):

```bash
K=/opt/kafka/bin
```

All commands below use `docker exec kafka-1 $K/...`. For the ones that need more than one terminal, open one terminal tab per consumer/producer.

Quick glossary:
- **rf** (replication factor): number of copies of each partition.
- **ISR** (in-sync replicas): replicas that keep up with the leader.
- **acks** (acknowledgements): how many replicas confirm a write.
- **KRaft** (Kafka Raft): Kafka mode without ZooKeeper, where the brokers themselves form the control quorum.

## Experiment 1: key and partition

**Goal:** see that the same key always lands in the same partition, and that several keys can share a partition.

Create the topic (3 partitions, rf=3):

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --create --topic lab-keys --partitions 3 --replication-factor 3
```

Publish 6 messages with keys (format `key:value`):

```bash
printf 'account-A:deposit-100\naccount-B:deposit-50\naccount-A:withdraw-30\naccount-C:deposit-70\naccount-A:deposit-50\naccount-B:withdraw-20\n' | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab-keys --property parse.key=true --property key.separator=:
```

Read them showing partition, offset and key:

```bash
docker exec kafka-1 $K/kafka-console-consumer.sh --bootstrap-server kafka-1:9092 --topic lab-keys --from-beginning --timeout-ms 8000 --property print.key=true --property print.partition=true --property print.offset=true
```

(The `TimeoutException` at the end is expected: it is `--timeout-ms` ending the read.)

**Predict first:** how many partitions will each account appear in? Can two accounts land in the same partition?

**Observe:**
- All `account-A` messages in the same partition, with increasing offsets (order is kept).
- Different accounts can share a partition (in a reference run, `account-A` and `account-C` landed together).
- Repeat publishing without a key (remove `parse.key` and send only the value) and watch the messages spread out.

## Experiment 2: order only within a partition

**Goal:** confirm there is no global order across partitions.

Use the topic from experiment 1. Read it again with the consumer above and compare the read order **across** partitions (which can vary) with the order **within** each partition (always increasing).

**Predict:** if I read the whole topic twice, will the global message order be the same both times?

**Observe:** the interleaving across partitions changes between runs; the offset sequence within each partition does not.

## Experiment 3: consumer groups and rebalance

**Goal:** see partitions being split among consumers of the same group and reassigned when one leaves or joins.

Open **3 terminals** and, in each one, start a consumer with the same group (one per terminal, one at a time, observing between steps):

```bash
docker exec -it kafka-1 $K/kafka-console-consumer.sh --bootstrap-server kafka-1:9092 --topic lab-keys --group g1 --property print.partition=true
```

In a fourth terminal, follow the distribution:

```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --describe --group g1
```

Sequence:
1. Start **1** consumer. Run `--describe`.
2. Start the **2nd**. Run `--describe`.
3. Start the **3rd**. Run `--describe`.
4. Start a **4th** (the topic has 3 partitions).
5. Stop one of the consumers (Ctrl+C) and run `--describe` again.
6. In another terminal, publish messages with varied keys (command from experiment 1) and see which consumer receives what.

**Predict:** how many partitions does each consumer get at each step? What does the 4th consumer do?

**Observe:**
- 1 consumer reads all 3 partitions; with 3, one each.
- The 4th stays idle (no partition): maximum parallelism is the number of partitions.
- When a consumer leaves, its partitions go to the remaining ones (rebalance), and there may be a short pause.
- Messages with the same key always reach the same consumer (while the assignment does not change).

## Experiment 4: two groups, same message

**Goal:** see that different groups read everything independently.

Start a consumer with `--group g1` and another with `--group g2`, both with `--from-beginning`, and publish messages.

**Predict:** how many consumers does each message reach?

**Observe:** each message reaches both groups. Within a single group, only one consumer.

## Experiment 5: broker failure and ISR

**Goal:** see the leader change and the ISR shrink when a broker goes down.

Check the initial state:

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --describe --topic lab-keys
```

Stop a broker (here, `kafka-2`):

```bash
docker compose -f docker/docker-compose.yml stop kafka-2
```

Run `--describe` again (wait a few seconds). Publish with `acks=all`:

```bash
printf 'account-A:failure-test\n' | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab-keys --producer-property acks=all --property parse.key=true --property key.separator=:
```

Start the broker again and watch the ISR recover:

```bash
docker compose -f docker/docker-compose.yml start kafka-2
```

**Predict:** which partitions change leader? Which broker does each partition's ISR lose? Does an `acks=all` write work with 1 broker down?

**Observe:**
- Partitions that had broker 2 as leader get a different leader.
- The ISR of every partition loses broker 2 and returns to 3 members after it comes back.
- The write works (an ISR of 2 satisfies `min.insync.replicas=2`).
- The preferred leader may take a while to return to the original broker.

## Experiment 6: `acks` and `min.insync.replicas`

**Goal:** see a write rejected for lack of replicas, and the difference between `acks=all` and `acks=1`.

Create a topic with `min.insync.replicas=3` (equal to rf), so that **any** broker being down blocks `acks=all` writes. This keeps the KRaft quorum intact (2 of 3 nodes), unlike stopping 2 brokers.

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --create --topic lab-durability --partitions 1 --replication-factor 3 --config min.insync.replicas=3
```

With all 3 brokers up, publish with `acks=all` (should work):

```bash
echo "k:ok" | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab-durability --producer-property acks=all --property parse.key=true --property key.separator=:
```

Stop a broker other than `kafka-1` (which serves the commands):

```bash
docker compose -f docker/docker-compose.yml stop kafka-3
```

Check the ISR and try to publish with `acks=all`, then with `acks=1`:

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --describe --topic lab-durability
```
```bash
echo "k:failure" | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab-durability --producer-property acks=all --producer-property delivery.timeout.ms=8000 --producer-property request.timeout.ms=4000 --property parse.key=true --property key.separator=:
```
```bash
echo "k:acks1" | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab-durability --producer-property acks=1 --property parse.key=true --property key.separator=:
```

Start the broker again:

```bash
docker compose -f docker/docker-compose.yml start kafka-3
```

**Predict:** what happens with `acks=all` when the ISR has 2 and the minimum is 3? And with `acks=1`?

**Observe:**
- With `acks=all`: `NOT_ENOUGH_REPLICAS` warnings and, once the retries are exhausted, the message is not written.
- With `acks=1`: the write is accepted, because `min.insync.replicas` only applies with `acks=all`.
- After the broker is back, the ISR returns to 3 replicas.

## Experiment 7: offset replay

**Goal:** re-read the history of an already consumed group.

Consume everything with a new group and let it finish:

```bash
docker exec kafka-1 $K/kafka-console-consumer.sh --bootstrap-server kafka-1:9092 --topic lab-keys --group g-replay --from-beginning --timeout-ms 8000 --property print.partition=true --property print.offset=true
```

Check the committed offset and the lag (should be 0):

```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --describe --group g-replay
```

Simulate the reset (`--dry-run` only shows what it would do), then run it (the group must have no active consumers):

```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --group g-replay --topic lab-keys --reset-offsets --to-earliest --dry-run
```
```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --group g-replay --topic lab-keys --reset-offsets --to-earliest --execute
```

Run the first command's consumer again, without `--from-beginning`.

**Predict:** does the consumer re-read the messages? Does `--from-beginning` make a difference in this second run?

**Observe:**
- After the reset, the group re-reads everything even without `--from-beginning`, because the committed offset now points to the start.
- Before the reset, running with `--from-beginning` on a group that already has an offset does nothing: the option only applies when there is no committed offset.
- Other ways to reposition: `--to-offset N`, `--to-datetime YYYY-MM-DDThh:mm:ss.sss`, `--shift-by -N`.

## Cleanup

Remove the topics and groups created by the experiments:

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --delete --topic lab-keys
```
```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --delete --topic lab-durability
```
```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --delete --group g1
```

(Repeat the last one for `g2` and `g-replay`.) If a group still has active members, stop the consumers first.

## Log table

Copy and fill in for each experiment.

| # | I predict that... | What happened... | What surprised me |
|---|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |
| 4 | | | |
| 5 | | | |
| 6 | | | |
| 7 | | | |

## Notes

- The experiment topics use the `lab-` prefix so they do not mix with the application topics created by `docker/create-topics.sh`.
- Several of these commands (publishing with keys, reading with partition and offset, consumer group `--describe`, `--reset-offsets` and the `acks` test of experiment 6) were run against the project cluster before being documented. The multi-terminal parts (experiments 3, 4 and 5) were not run.
- Experiment 6 uses `min.insync.replicas=3` on purpose. Stopping 2 of 3 brokers also makes the KRaft quorum lose its majority (the nodes are brokers and controllers at the same time), which would mix two effects.
