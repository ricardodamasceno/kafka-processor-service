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

# Right after the registration Connect has no status yet and answers 404, so wait a few seconds for it.
echo "Status:"
for _ in 1 2 3 4 5 6 7 8 9 10; do
  status="$(curl -sS "$CONNECT_URL/connectors/$CONNECTOR_NAME/status")"
  case "$status" in
    *'"error_code":404'*) sleep 2 ;;
    *) break ;;
  esac
done
echo "$status"
