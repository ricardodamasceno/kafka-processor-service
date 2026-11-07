#!/bin/sh
# Cria os tópicos do laboratório. Idempotente (--if-not-exists).
set -e

BOOTSTRAP="${BOOTSTRAP_SERVERS:-kafka-1:9092,kafka-2:9092,kafka-3:9092}"
KAFKA_TOPICS="/opt/kafka/bin/kafka-topics.sh"

# nome:partições:replicação
TOPICS="
payments.transfers:6:3
payments.transfers.dlq:3:3
ledger.events:6:3
ad.clicks:6:3
ad.clicks.aggregated:3:3
"

for entry in $TOPICS; do
  name=$(echo "$entry" | cut -d: -f1)
  partitions=$(echo "$entry" | cut -d: -f2)
  replication=$(echo "$entry" | cut -d: -f3)
  echo "Criando tópico $name (partitions=$partitions, rf=$replication)"
  "$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$name" --partitions "$partitions" --replication-factor "$replication"
done

echo "Tópicos existentes:"
"$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --list
