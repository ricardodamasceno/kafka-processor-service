# 0009. Record movements in a consumer of the update events

- Status: Accepted
- Date: 2026-11-11

## Context

A transaction that passes the validation reaches `PROCESSING`, which is the stage where it is actually carried out ([ADR 0008](0008-validate-transactions-in-a-consumer-of-created-events.md)). Carrying it out means moving money, and a `movements` table records each movement. The status change is published as a `TRANSACTION_PROCESSING` event on `payments-transactions-update` ([ADR 0006](0006-translate-changes-into-transaction-events.md)), so that event is the natural trigger.

## Decision

- **A consumer reads the update events.** `TransactionUpdatedConsumer` (group `movement-recorder`) reads `payments-transactions-update`.
- **Only `TRANSACTION_PROCESSING` records a movement.** `COMPLETED` and `FAILED` are ignored: a failed transaction moved no money, and a completed one already has its movement from the `PROCESSING` stage.
- **The type of the movement comes from the type of the transaction:** a `WITHDRAWAL` is a `DEBIT` and anything else (a `DEPOSIT`) is a `CREDIT`. The rule is `MovementType.of`, in the domain.
- **The consumer calls `MovementService.record`** with the user, the transaction, the amount and the type, all taken from the event, which carries the whole state of the transaction.
- **Once the movement is saved, the transaction is completed.** The consumer then calls `TransactionService.complete`, which moves it from `PROCESSING` to `COMPLETED` with one atomic `UPDATE ... WHERE status = 'PROCESSING'`. The change is published as `TRANSACTION_COMPLETED`, which this consumer ignores.
- **A repeated event is harmless.** A transaction has at most one movement of each type (unique index on `(transaction_id, type)`). The second delivery gets a `ConflictException`, which the consumer treats as "the movement is already saved" and goes on to complete the transaction. Completing only moves a transaction that is still `PROCESSING`, so it is harmless too.
- **The two writes are not atomic, and the retry repairs it.** If the movement is saved but completing the transaction fails, the message is retried; the retry finds the movement (conflict) and completes the transaction. A transaction is never completed without its movement, because a failure when saving the movement stops the consumer before the completion.
- **Failures:** an error such as the database being down is retried 3 times, 1 second apart, and then the message goes to `payments-transactions-update-dlq`. A message that cannot be understood, or a `PROCESSING` event without the data of the movement, goes there without retries.

Alternatives considered:

- **Record on `TRANSACTION_COMPLETED`.** Nothing else completes a transaction, so no movement would ever be recorded: here the completion is a consequence of the movement, not the trigger of it.
- **Record the movement in the validation step, in the same database transaction as the move to `PROCESSING`.** It would be atomic, but it mixes two steps that ADR 0008 keeps apart, and ties the validation to the movements.
- **Check whether the movement exists before inserting.** Two instances could both see none. The unique index decides, and the check would only add a read.

## Consequences

- The movement is recorded **after** the transaction is already `PROCESSING`, so there is a moment when the transaction is `PROCESSING` and has no movement. A consumer that is down, or a message in the dead letter queue, leaves it that way, and nothing replays it.
- The movement is recorded when the transaction **starts** being carried out, not when it finishes. If a later step fails the transaction, nothing reverses the movement yet.
- A transaction is `COMPLETED` once its movement is saved. Nothing else carries it out: recording the movement is, for now, all that carrying it out means.
- If the message goes to the dead letter queue, the transaction stays `PROCESSING` and, depending on where it failed, may or may not have its movement.
- Events that were published before this consumer existed are read when its group starts, because new groups read from the beginning of the topic, so it records movements for transactions that are already `PROCESSING`.
