#!/bin/sh
# Registers (or updates) the Debezium change data capture connector in Kafka Connect.
# Idempotent: PUT creates the connector if it does not exist and updates its configuration if it does.
# Run it after the tables listed in "table.include.list" exist (./gradlew flywayMigrate), because the connector
# creates a publication on them.
set -e

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
CONNECTOR_NAME="cdc-connector"
CONFIG="$(dirname "$0")/debezium/cdc-connector.json"

curl -sS --fail-with-body -X PUT "$CONNECT_URL/connectors/$CONNECTOR_NAME/config" \
  -H "Content-Type: application/json" \
  -d @"$CONFIG"
echo
echo "Status:"
curl -sS "$CONNECT_URL/connectors/$CONNECTOR_NAME/status"
echo
