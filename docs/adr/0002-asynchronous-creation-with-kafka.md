# 0002. Asynchronous creation with Kafka

- Status: Accepted
- Date: 2026-11-07

## Context

Creating transactions was synchronous: the REST endpoint wrote straight to PostgreSQL. We want the write path to be decoupled from the database, absorb bursts, and be processed by consumers that can scale independently.

## Decision

**Creation flow.** `POST /transactions` validates the request exactly as before (DTO validation plus the domain rules), generate the id, publish an event to Kafka and answer `202 Accepted` with an empty body (the client does not receive the id). A consumer reads the event and writes the record. `GET`, `PUT` and `DELETE` stay synchronous.

- Topics: `payments-transactions` for transactions, with the dead letter queue `payments-transactions-dlq`.
- The producer uses `acks=all` (with `min.insync.replicas=2` on the topics) and the idempotent producer. The API waits for the broker acknowledgement and answers `503` if it does not arrive within `app.messaging.send-timeout`, so a `202` means the event is safely stored in Kafka.
- The message key is the entity id. Events of the same entity go to the same partition and are consumed in order; random UUIDs spread the load evenly.
- Messages are plain JSON strings (`StringSerializer`), with a message class separate from the domain class.
- Delivery is at-least-once. The id is generated before publishing, so the consumer's `persist` is idempotent: a duplicate event overwrites the same row.
- Consumers commit the offset after each message is processed. A failure is retried 3 times, 1 second apart, then the message goes to the DLQ. Malformed payloads and broken domain rules go to the DLQ without retries, because retrying cannot fix them.
- The domain object is rebuilt on the consumer side, so the domain rules are checked again before anything is written.

**If the whole CRUD becomes asynchronous: one topic per entity, with the action in the message.** Use the `payments-transactions` topic carrying events such as `CREATED`, `UPDATED` and `DELETED` (the type in the payload and in a header), keyed by the entity id. Do not create one topic per action, and do not put every entity in a single topic.

- Kafka orders messages only inside a partition. Create, update and delete of the same entity must be processed in that order, so they have to share a topic and a key. With one topic per action there is no order between topics: an update can be processed before the create it depends on.
- A single topic for all entities would mix different schemas, retention, scaling and consumers, and every consumer would have to filter what it does not care about.
- Prefer events that state a fact (`TransactionUpdated`) over commands (`UpdateTransaction`). A delete can be a message with a null value (a tombstone) if the topic is compacted.
- The key must be the identity whose order matters. If a rule needs ordering per account (for example a balance), the key for transactions should become the account id.

## Consequences

- The system is eventually consistent for creation: `GET` returns 404 until the consumer persists the record, and `update` or `delete` sent too early also return 404.
- A new component to operate: consumer lag and the DLQs must be monitored, and DLQ messages need reprocessing or inspection by hand.
- Creating a record has no database write in the request, so there is no dual write between the database and Kafka.
- Everything that reads Kafka must tolerate duplicates.
- Update and delete remain synchronous for now, so a mixed model exists until they are moved too.

## Update (2026-11-10)

The click entity was removed from the service, so only transactions remain. The topic `payments.transfers` was renamed to `payments-transactions` (and its DLQ to `payments-transactions-dlq`) to match the entity name, and topic names now use hyphens as the only separator. The unused `ledger.events` topic was removed. The decision above is unchanged.
