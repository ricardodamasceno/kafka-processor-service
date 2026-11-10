#!/bin/sh
# Creates the project topics. Idempotent (--if-not-exists).
set -e

BOOTSTRAP="${BOOTSTRAP_SERVERS:-kafka-1:9092,kafka-2:9092,kafka-3:9092}"
KAFKA_TOPICS="/opt/kafka/bin/kafka-topics.sh"

# name:partitions:replication
TOPICS="
payments-transactions:6:3
payments-transactions-dlq:3:3
"

for entry in $TOPICS; do
  name=$(echo "$entry" | cut -d: -f1)
  partitions=$(echo "$entry" | cut -d: -f2)
  replication=$(echo "$entry" | cut -d: -f3)
  echo "Creating topic $name (partitions=$partitions, replication=$replication)"
  "$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$name" --partitions "$partitions" --replication-factor "$replication"
done

echo "Existing topics:"
"$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --list
