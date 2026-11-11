# 0008. Validate transactions in a consumer of the created events

- Status: Accepted
- Date: 2026-11-11

## Context

A transaction is created as `PENDING`. Its life has two stages: the first, while it is `PENDING`, is where it is **validated**; the second starts when it reaches `PROCESSING`, and is where it is actually carried out. [ADR 0006](0006-translate-changes-into-transaction-events.md) publishes a `TRANSACTION_CREATED` event for each new transaction, which is the work for the first stage. Until now nothing read it, so every transaction stayed `PENDING`.

## Decision

- **A consumer reads the creation events.** `TransactionCreatedConsumer` (group `transaction-validator`) reads `payments-transactions-create` and calls `TransactionService.validate`.
- **The validation, for now, is that the user is `ACTIVE`.** The status is read from the cache first (`UserStatusCache`, see [ADR 0007](0007-cache-the-status-of-users-in-redis.md)). When the cache has no entry, the database is read and the cache is filled for the next time. A user that does not exist counts as not active.
- **The outcome decides the next status.** An active user moves the transaction from `PENDING` to `PROCESSING`. Any other case moves it to `FAILED`.
- **The move is atomic and only happens while the transaction is still `PENDING`.** It is a single `UPDATE ... WHERE status = 'PENDING'`, so the database decides who wins. An event delivered twice, or handled by two instances, validates the transaction once: the second one finds it no longer `PENDING` and changes nothing.
- **The allowed moves are in the domain:** `TransactionStatus.canMoveTo`. A `PENDING` transaction goes to `PROCESSING` or `FAILED`, a `PROCESSING` one goes to `COMPLETED` or `FAILED`, and `COMPLETED` and `FAILED` are final.
- **The consumer publishes no event.** The status change is a change of the `transactions` table, so Debezium captures it and the translator turns it into `TRANSACTION_PROCESSING` or `TRANSACTION_FAILED` on `payments-transactions-update`, as described in ADR 0006.
- **Failures:** an error such as the database being down is retried 3 times, 1 second apart, and then the message goes to `payments-transactions-create-dlq`, with the transaction still `PENDING`. A message that cannot be understood, or that has no user, goes to the dead letter queue without retries.
- **`POST /transactions` checks only that the user exists.** An unknown user is still a 404 and no row is created, because a transaction for a user that does not exist makes no sense and the table references the user. Whether the user is active is **not** checked in the request: the `PENDING` stage is the only place where it is validated. A request for a user that is not active is accepted (202), and the transaction ends as `FAILED` a moment later.

Alternatives considered:

- **Validate only in the request.** A transaction would never be validated later, and there would be no stage in which more validations (limits, balance) could be added without slowing the request.
- **Check the status in the request as well as in the consumer.** It was done at first, so a mistake failed at once with a 409. It was removed so that the validation lives in one place and the request does not decide what the `PENDING` stage is for. The existence of the user is the exception, because without a user there is nothing to validate or store.
- **Read the status only from the database.** Simpler, but every validation would read the database for a value that changes rarely.
- **Read the transaction, decide, and then update it.** Two instances, or a duplicate event, could both see `PENDING` and both move it. The conditional update removes the window.
- **Have the consumer publish the resulting event itself.** That writes to the database and to Kafka without a common transaction. Letting Debezium publish the change avoids the gap.

## Consequences

- A transaction now leaves `PENDING` as `PROCESSING` or `FAILED`. The next stage carries it out: the movement recorder ([ADR 0009](0009-record-movements-in-a-consumer-of-the-update-events.md)) records the movement and completes it.
- **A client no longer learns synchronously that a user is not active.** It receives 202 and has to read the transaction to see `FAILED`. Together with the next point, it cannot tell why.
- **A `FAILED` transaction records no reason.** Today the only reason is that the user is not active or does not exist, but it is not stored and not in the event. A reason is the natural next addition.
- **The cached status is trusted, even when it is wrong.** If a user was blocked and the cache still says `ACTIVE`, for example because the write to Redis was lost, their transactions are moved to `PROCESSING` until the entry expires (one minute). This was accepted in ADR 0007. A rule that must never be wrong has to read the database.
- The validation fills the cache after a database read, so a missing entry costs one database read, and the short expiry is workable.
- A message that goes to the dead letter queue leaves its transaction `PENDING`, and nothing replays it or times it out. A `PENDING` transaction that is stuck stays that way until someone acts.
- Events published before the `userId` existed lack it, so a new consumer group that starts from the beginning of the topic sends them to the dead letter queue.
- The consumer needs idempotency only in the sense described above, and it has it: it is the conditional update that makes a repeated event harmless.
