# Architecture

The project follows **Clean Architecture**: the code is organized in layers, and dependencies only point **inward**, from the frameworks toward the business rules. The business rules never depend on Spring, JPA or HTTP.

## The dependency rule

```
adapter.web ──────────┐
adapter.persistence ──┼──> application ──> domain
adapter.messaging ────┘
config (wires everything together)
```

- `domain` depends on nothing.
- `application` depends only on `domain`.
- `adapter.*` depend on `application` and `domain`.
- `config` depends on everything, because its job is to connect the pieces.

Nothing in `domain` or `application` imports `org.springframework`, `jakarta.persistence` or any HTTP class. This is what makes them easy to test and easy to keep when a framework changes.

## Packages

Base package: `com.event.producer`.

### `domain`: enterprise business rules

What lives here: the entities and the rules that are always true, plus the **ports** (interfaces) that describe what the business needs from the outside world.

| Class | Role |
|---|---|
| `domain.transaction.Transaction` | Entity: an immutable record that validates itself in the constructor. It belongs to a user (`userId`). Money is `BigDecimal`, amount is always positive with at most 2 decimals |
| `domain.transaction.TransactionType` | Enum: `DEPOSIT`, `WITHDRAWAL` |
| `domain.transaction.TransactionStatus` | Enum: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`. Where a transaction is in its life. `canMoveTo` holds the allowed moves: `PENDING` goes to `PROCESSING` or `FAILED`, `PROCESSING` goes to `COMPLETED` or `FAILED`, and `COMPLETED` and `FAILED` are final |
| `domain.transaction.TransactionRepository` | Port: the contract for storing transactions. Defined here, implemented elsewhere |
| `domain.user.User` | Entity: an immutable record. The name is trimmed and the email is trimmed and lower-cased, so two emails that differ only in case are the same user. Limits: name up to 200 characters, email up to 320 and in a valid format |
| `domain.user.UserRepository` | Port: the contract for storing users and checking whether an email is taken |
| `domain.movement.Movement`, `MovementType`, `MovementRepository` | A movement of money in the account of a user, caused by a transaction: an immutable record with a positive amount (`BigDecimal`, at most 2 decimals) and a `CREDIT` or `DEBIT` type. The port has one operation, `save` |
| `domain.user.UserStatus` | Enum: `ACTIVE`, `INACTIVE`, `BLOCKED`. Where a user is in their life. A new user is `ACTIVE`. `User.withStatus` returns a copy of the user with another status, and any status can be set from any other |
| `domain.shared.DomainValidationException` | Thrown when a domain rule is violated |

Why: if a rule such as "an amount must be positive" lives in the entity, it is enforced no matter who creates a `Transaction` (an HTTP call, a Kafka consumer, a test).

### `application`: application business rules (services)

What lives here: the operations the system supports. Each service orchestrates domain objects and calls ports.

| Class | Role |
|---|---|
| `application.transaction.TransactionService` | `create`, `validate`, `get`, `list`, `update`, `delete` for transactions. `complete` is the last step: it moves a `PROCESSING` transaction to `COMPLETED` (atomic, only from `PROCESSING`). `validate` is the first step of the life of a transaction, while it is `PENDING`: it checks that the user is `ACTIVE` (through `UserService.statusOf`) and moves the transaction to `PROCESSING`, or to `FAILED` otherwise. The move is atomic and only happens while the transaction is still `PENDING`. `create` checks that the user exists, through the `UserRepository` port. Whether the user is active is not checked there: it is the job of the validation step |
| `application.transaction.TransactionEvent` | Something that happened to a transaction: a type, the complete state of the transaction, and an id that stays the same if the event is produced twice |
| `application.transaction.TransactionEventType` | `TRANSACTION_CREATED`, `TRANSACTION_PROCESSING`, `TRANSACTION_COMPLETED`, `TRANSACTION_FAILED` |
| `application.transaction.TransactionEventPublisher` | Outbound port: how the application announces a transaction event. Implemented by the Kafka adapter |
| `application.user.UserService` | `register`: validates, assigns an id and the registration time (from the `Clock`), refuses an email that is already registered, saves the user as `ACTIVE` and caches the status. `updateStatus`: sets any of the three statuses, from any status, and refreshes the cached status. `statusOf`: the status of a user, from the cache first and from the database when the cache has no entry, filling the cache after a database read |
| `application.movement.MovementService` | `record`: validates the data, assigns an id and the creation time (from the `Clock`) and saves the movement. It throws `ConflictException` when the transaction already has a movement of that type. Nothing calls it yet: it will be triggered by the update event of a transaction |
| `application.user.UserStatusCache` | Outbound port: a fast copy of the status of each user, kept outside the database. `put` and `find` are best effort and must not throw: when the cache is unavailable `find` answers "no entry". Implemented by the Redis adapter |
| `application.transaction.ValidationOutcome` | What `validate` did: `PROCESSING`, `FAILED`, or `NOT_PENDING` (the transaction was not `PENDING` any more, so nothing changed) |
| `application.shared.NotFoundException` | Thrown when a resource does not exist |
| `application.shared.ConflictException` | Thrown when a request conflicts with what already exists, such as a registered email |
| `application.shared.PublishFailedException` | Thrown when an event could not be published |

`create` validates, assigns an id and saves the transaction as `PENDING`. What happens next is driven by the changes of the `transactions` table, which Debezium publishes to Kafka and a translator turns into transaction events: the request never calls Kafka. See [ADR 0005](adr/0005-pending-transactions-and-cdc-events.md), [ADR 0006](adr/0006-translate-changes-into-transaction-events.md) and [debezium.md](debezium.md).

Why a service per entity: it keeps the number of files small for a simple CRUD. A service takes plain values as parameters, so it does not depend on the HTTP JSON format. If a service grows too large, or an operation gets rich business logic (for example a withdrawal), that operation can be extracted into its own use case class.

The services have no Spring annotations on purpose (see `config`).

### `adapter`: interface adapters

Adapters convert data between the format the inner layers use and the format an external technology uses.

**`adapter.web`** (the driving side: something calls us)

| Class | Role |
|---|---|
| `TransactionController` | Receives HTTP, calls a service, returns HTTP. No business rule |
| `TransactionRequest` | Input DTO (data transfer object) to create a transaction, with Bean Validation (`@NotNull`, `@NotBlank`, `@Positive`...). It carries the `userId` of the owner |
| `TransactionUpdateRequest` | Input DTO to update a transaction. It has no `userId` and no status, because an update cannot change the owner or the status |
| `TransactionResponse` | Output DTO. It includes the `userId` and the `status` |
| `UserController` | `POST /users` registers a user, and `PUT /users/{id}/status` sets its status |
| `UserRequest` | Input DTO. It only checks that the fields are present; format and lengths are rules of the domain `User` |
| `UserResponse` | Output DTO |
| `UserStatusRequest` | Input DTO of `PUT /users/{id}/status`: only the `status`, which must be present and one of `ACTIVE`, `INACTIVE` or `BLOCKED` (uppercase) |
| `ApiExceptionHandler` | Maps `NotFoundException` to 404, `ConflictException` to 409 and `DomainValidationException` to 400 |

DTOs are separate from domain entities so the API contract and the domain model can change independently.

**`adapter.persistence`** (the driven side: we call something)

| Class | Role |
|---|---|
| `TransactionJpaEntity` | JPA class that maps to the table. It is **not** the domain entity |
| `TransactionJpaRepository` | Spring Data interface (Spring generates the SQL access) |
| `TransactionRepositoryAdapter` | Implements the domain port and delegates to Spring Data; it does the persistence only. `moveStatus` is a single `UPDATE ... WHERE status = :status`, so the database decides who wins when two callers move the same transaction |
| `UserJpaEntity`, `UserJpaRepository`, `UserMapper` | The same three pieces for the `users` table. The repository checks the email with `lower()`, which is what the unique index covers |
| `UserRepositoryAdapter` | Implements `UserRepository`. When the unique index on the email rejects an insert, it throws `ConflictException` |
| `MovementJpaEntity`, `MovementJpaRepository`, `MovementMapper`, `MovementRepositoryAdapter` | The same pieces for the `movements` table. The adapter turns a violation of the unique index `uq_movements_transaction_id_type` into `ConflictException` |
| `TransactionMapper` | Converts between domain entity and JPA entity (`toEntity` / `toDomain`). Its only responsibility is this translation. It is a stateless utility class with static methods, not a Spring bean |

Why two entity classes: JPA needs a no-arg constructor, annotations and a mutable shape. Keeping that out of the domain keeps the domain pure. The cost is the mapping step, which lives in the mapper classes so it can be tested on its own.

Why `TransactionJpaRepository` needs no `@Repository`: it is only an interface. Spring Data JPA scans for interfaces that extend `JpaRepository` and creates the implementation at runtime (a proxy backed by `SimpleJpaRepository`, which uses the JPA `EntityManager` and Hibernate).

**`adapter.cache`** (the driven side: Redis)

| Class | Role |
|---|---|
| `RedisUserStatusCache` | Implements the `UserStatusCache` port. One entry per user: the key is `user:status:<userId>`, the value is the status name, and it expires after `app.cache.user-status-ttl`. A failure to reach Redis is logged as a warning and swallowed on a write, and counts as "no entry" on a read, as does an entry that is not a status |
| `CacheProperties` | The settings under `app.cache`, read from `application.yml` |

**`adapter.messaging`** (Kafka: both driving and driven)

The package `cdc` is the only part that knows how the changes are captured. Everything else works with transaction events.

| Class | Role |
|---|---|
| `cdc.TransactionChangeConsumer` | `@KafkaListener` class (the **translator**, the only reader of `transactions-cdc-events`). Reads each change, asks the translator for an event and publishes it |
| `cdc.TransactionChangeTranslator` | The rules that turn a row change into a `TransactionEvent`, or into nothing. The only class that interprets `before` and `after` |
| `cdc.TransactionChangeMessage` | The JSON shape of a change as Debezium publishes it (`op`, `before`, `after`). The row fields use the column names, so the table schema is the contract of that topic |
| `transaction.TransactionEventKafkaPublisher` | Implements the event publisher port (the **producer**). Key = transaction id. `TRANSACTION_CREATED` goes to `payments-transactions-create`, the other events to `payments-transactions-update` |
| `transaction.TransactionEventMessage` | The JSON shape of a transaction event: the contract of those topics, separate from the domain class |
| `transaction.TransactionCreatedConsumer` | `@KafkaListener` class (the **validator**) of `payments-transactions-create`. Reads each `TRANSACTION_CREATED` event and calls `TransactionService.validate`. An event it cannot understand, or that has no user, goes to the dead letter queue without retries |
| `transaction.TransactionUpdatedConsumer` | `@KafkaListener` class (the **movement recorder**) of `payments-transactions-update` (group `movement-recorder`). On `TRANSACTION_PROCESSING` it calls `MovementService.record`: a debit for a `WITHDRAWAL`, a credit for anything else (`MovementType.of`). Once the movement is saved (or was already), it calls `TransactionService.complete`. The other update events are ignored. An event it cannot understand goes to the dead letter queue without retries |
| `KafkaMessageSender` | Sends a message and waits for the broker acknowledgement (`acks=all`) |
| `JsonMessageCodec` | Converts messages to and from JSON text. Messages are plain JSON strings on the topic |
| `KafkaErrorHandlingConfig` | Retries a failed message, then sends it to the dead letter queue (DLQ). Bad payloads and broken domain rules skip the retries |
| `MessagingProperties` | Topic names, send timeout and retry policy, read from `application.yml` |

Consumer groups: `transaction-event-translator` (the translator, on `transactions-cdc-events`) and `transaction-validator` (the validator, on `payments-transactions-create`). All instances of the application in a group share the partitions.

### `config`: frameworks and wiring

`ServiceConfig` is the **composition root**: it creates each service and injects the ports (the repository, and the `Clock` when a service needs the current time). Spring picks the adapter that implements each port. This is the only place that knows both sides, and it keeps the application layer free of Spring.

## How a request flows

`POST /transactions`:

1. `TransactionController` receives the JSON as a `TransactionRequest` and validates its format.
2. It calls `TransactionService.create(userId, accountId, type, amount, occurredAt)`.
3. The service creates a `Transaction` with an id and the status `PENDING`. The domain validates its rules first, so invalid data is a **400** whatever the user.
4. The service looks for the user through the `UserRepository` port. If the user does not exist it throws `NotFoundException` (**404**) and nothing is saved, because a transaction for a user that does not exist makes no sense. Whether the user is `ACTIVE` is **not** checked here: a request for a user that is not active is accepted, and the validation step fails the transaction a moment later.
5. `TransactionRepositoryAdapter` converts the `Transaction` to a `TransactionJpaEntity` and saves it.
6. The API answers **202 Accepted** with an empty body (no id and no `Location` header). The row is already in the database, so `GET /transactions/{id}` returns 200 straight away, with the status `PENDING`. Since the response does not carry the id, a client finds the record with `GET /transactions` (most recent first).
7. Debezium reads the insert from the PostgreSQL write-ahead log and publishes the change to `transactions-cdc-events`.
8. `TransactionChangeConsumer` reads the change, `TransactionChangeTranslator` turns it into a `TRANSACTION_CREATED` event (the complete state of the transaction, including the `userId`, with the event id `<transactionId>:PENDING`), and `TransactionEventKafkaPublisher` publishes it to `payments-transactions-create` with the transaction id as key. The validator reads that topic, as described below.

When the status changes later (`PROCESSING`, `COMPLETED` or `FAILED`), the same path publishes the matching event to `payments-transactions-update`. A change that does not alter the status, such as a `PUT` that only changes the amount, and a delete produce no event.

**Validation, the `PENDING` stage.** `TransactionCreatedConsumer` (group `transaction-validator`) reads the `TRANSACTION_CREATED` event and calls `TransactionService.validate`. This is where a transaction is validated, and carrying it out is a later stage:

1. `UserService.statusOf` looks for the status of the user in Redis (`user:status:<userId>`). If there is no entry, it reads the database and writes the status to Redis for the next time.
2. If the user is `ACTIVE`, the transaction moves from `PENDING` to `PROCESSING`. If the user is not active, or does not exist, it moves to `FAILED`.
3. The move is one `UPDATE ... WHERE status = 'PENDING'`. If the transaction is no longer `PENDING` (the same event delivered twice, or handled by another instance), nothing changes and the outcome is `NOT_PENDING`.
4. The status change is a change of the `transactions` table, so it follows the usual path: Debezium, the translator, and an event `TRANSACTION_PROCESSING` or `TRANSACTION_FAILED` on `payments-transactions-update`. The validator publishes nothing itself.

If the validation throws (for example the database is down), the message is retried and then goes to `payments-transactions-create-dlq`, and the transaction stays `PENDING`. See [ADR 0008](adr/0008-validate-transactions-in-a-consumer-of-created-events.md).

**Recording the movement, the `PROCESSING` stage.** `TransactionUpdatedConsumer` (group `movement-recorder`) reads `payments-transactions-update`. For a `TRANSACTION_PROCESSING` event it calls `MovementService.record(userId, transactionId, amount, type)`, with `DEBIT` for a `WITHDRAWAL` and `CREDIT` otherwise. The movement is a new row of `movements`, which Debezium captures like any other table. Delivery is at least once, and the unique index on `(transaction_id, type)` makes a repeated event harmless: the consumer sees the `ConflictException` and does nothing. Once the movement is saved, or was already there, it calls `TransactionService.complete`, which moves the transaction from `PROCESSING` to `COMPLETED`; the change is published as `TRANSACTION_COMPLETED`, which the consumer ignores. The two writes are not atomic, and a retry repairs a failure between them: it finds the movement and completes the transaction. A failure is retried and then the message goes to `payments-transactions-update-dlq`. See [ADR 0009](adr/0009-record-movements-in-a-consumer-of-the-update-events.md).

`POST /transactions` rejects an unknown user at once (404) but does not look at the status of the user. The `PENDING` stage is the only place where the status is checked, so a request for a blocked or inactive user is answered with `202` and the transaction ends as `FAILED`.

`GET`, `PUT` and `DELETE` go straight to the database. Every change to the table, including these, is published to `transactions-cdc-events`. `PUT` keeps the current owner and status, because the request does not carry them: a `userId` sent in a `PUT` body is ignored.

`POST /users`:

1. `UserController` receives the JSON as a `UserRequest` and checks that `name` and `email` are present.
2. It calls `UserService.register(name, email)`.
3. The service creates a `User` (the domain trims and normalizes the data and checks its rules; a broken rule is a 400 with the reason) with a new id, the time from the `Clock` and the status `ACTIVE`. The status is not part of the request: a client cannot choose it, and a `status` field in the body is ignored.
4. If the email is already registered, regardless of case, the service throws `ConflictException`, which is a **409**.
5. `UserRepositoryAdapter` converts the `User` to a `UserJpaEntity` and saves it. If two requests with the same email pass step 4 at the same time, the unique index rejects the second one and the adapter reports it as the same `ConflictException`.
6. After the database write, `UserService` writes the status to the `UserStatusCache` (`user:status:<id>` in Redis). This is best effort: if Redis is unavailable the failure is logged and the registration still succeeds. A rejected registration (400 or 409) writes nothing to the cache.
7. The API answers **201 Created** with the user (`id`, `name`, `email`, `createdAt`, `status`). Unlike creating a transaction, the body carries the id, because registration is synchronous and the client needs the id to create transactions. There is no `Location` header.

```bash
curl -i -X POST http://localhost:8080/users -H "Content-Type: application/json" -d '{"name":"Alice Silva","email":"alice@example.com"}'
```

`PUT /users/{id}/status`:

1. `UserController` receives the JSON as a `UserStatusRequest`. The `status` must be present and one of `ACTIVE`, `INACTIVE` or `BLOCKED`, in uppercase. Anything else, a missing body or an `id` that is not a UUID is a **400**.
2. It calls `UserService.updateStatus(id, status)`.
3. The service loads the user (**404** if it does not exist), applies `User.withStatus` and saves it. Any status can be set from any other, and setting the status the user already has is accepted.
4. The service writes the new status to the cache, which also refreshes its expiry (best effort, as in the registration).
5. The API answers **200** with the user.

```bash
curl -i -X PUT http://localhost:8080/users/<id>/status -H "Content-Type: application/json" -d '{"status":"BLOCKED"}'
```

A user that is not `ACTIVE` cannot have a transaction carried out: its transactions are moved to `FAILED` by the validation step. `POST /transactions` does not check this. See [ADR 0007](adr/0007-cache-the-status-of-users-in-redis.md) and [ADR 0008](adr/0008-validate-transactions-in-a-consumer-of-created-events.md).

## Lombok

Lombok removes boilerplate at compile time (it is an annotation processor, `compileOnly`, so it is not present at runtime).

| Where | Annotations | Effect |
|---|---|---|
| JPA entities | `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@AllArgsConstructor` | Getters and the constructors JPA and the adapter need |
| Services, controllers, adapters | `@RequiredArgsConstructor` | Constructor injection of the `final` fields |
| Domain records | `@NonNull` on the components | Null checks in the constructor |

Records and DTOs already generate accessors, `equals` and `hashCode`, so they do not need Lombok for those.

### Lombok in the domain

Using Lombok in the domain is allowed. The "pure domain" idea is about not depending on frameworks **at runtime** (Spring, JPA, HTTP). Lombok only generates code at compile time and leaves no runtime dependency, although the source still imports `lombok.NonNull`. The trade-offs:

- `@NonNull` throws `NullPointerException`, not `DomainValidationException`. Over HTTP this never happens, because the request DTO already validates `@NotNull` and `@NotBlank` first.
- Lombok only covers null checks. Rules such as "not blank", "amount greater than zero" and "at most 2 decimal places" stay in the record constructor.
- A fully framework-free alternative is `Objects.requireNonNull` from the JDK, with a bit more code.

## Configuration

The pagination limits come from `application.yml` instead of being hard-coded in the controllers:

| Property | Used for |
|---|---|
| `app.pagination.default-page-size` | Page size used when the request does not send `size` |
| `app.pagination.max-page-size` | Upper bound for `size` |

The values are bound to the `PaginationProperties` record (`@ConfigurationProperties`), which also keeps the clamping logic in one place instead of duplicating it in each controller. The controller base path (`/transactions`) is written directly in `@RequestMapping`.
The Kafka and DLQ settings live under `app.messaging` (`send-timeout`, `retry.max-retries`, `retry.interval`, `topics.*`), bound to `MessagingProperties`. The connection settings (`bootstrap-servers`, `acks=all`, idempotent producer, `auto-offset-reset: earliest`, manual commit per record) are under `spring.kafka`. The Redis connection (`host`, `port` and the timeouts) is under `spring.data.redis`; see [redis.md](redis.md). The cache settings (`user-status-ttl`) are under `app.cache`, bound to `CacheProperties`.

## Testing strategy

- **Domain tests** (`TransactionTest`): pure Java, no Spring, no database.
- **Mapper tests** (`TransactionMapperTest`): check that the conversion in both directions loses nothing.
- **Service tests** (`TransactionServiceTest`): mock the ports (`TransactionRepository`, `UserRepository`, and `UserService` for the status) with Mockito. This works because services depend on interfaces, not on JPA, so no database is needed, and the tests check the calls made to the ports as well as the results. They cover: a created transaction is `PENDING` and owned by the user, an unknown user and a user that is `INACTIVE` or `BLOCKED` store nothing, invalid data is rejected before the user is looked up, `update` keeps the owner and the status, `delete`, and that `list` returns the page the repository gives.
- **User tests** (`UserTest`, `UserServiceTest`, `UserMapperTest`): normalization, the name and email rules, a duplicated email regardless of case, nothing stored when the data is invalid, the registration time coming from a fixed `Clock`, a new user being `ACTIVE`, any status being accepted from any other, the status written to the cache on registration and on every update, and nothing cached when a request is rejected.
- **Movement tests** (`MovementServiceTest`): the movement is saved with an id and the time from the clock; the amount is scaled to 2 decimals and an invalid one is rejected with nothing stored; a conflict reported by the repository is not hidden. That a transaction cannot have two movements of the same type is enforced by the unique index, so only a test against a real PostgreSQL would cover it.
- **Cache tests** (`RedisUserStatusCacheTest`): the key, the value and the expiry written to Redis (checked with a recording stand-in, without a Redis server), that a failure to reach Redis is swallowed, and that the expiry must be greater than zero. Reading is covered too: the status that was written, no entry, an entry that is not a status, and Redis being down all give the expected answer.
- **Validation tests** (also in `TransactionServiceTest`): an active user moves the transaction to `PROCESSING`; an `INACTIVE`, `BLOCKED` or unknown user moves it to `FAILED`; the same event twice validates once; and a transaction that is not `PENDING`, or does not exist, is left alone. Where the status comes from (the cache first, then the database, filling the cache) is `UserService.statusOf`, covered in `UserServiceTest`.
- **State machine test** (`TransactionStatusTest`): every move between the four statuses is checked against the allowed ones.
- **Event tests** (`TransactionEventTest`): the event type follows the status, and the event id is the same for the same transaction and status.
- **Translator tests** (`TransactionChangeTranslatorTest`): every rule of the translation table, plus the rejections: an update without the old row, a change without an operation, an incomplete row, and a row that breaks a domain rule.
- **Message contract tests** (`JsonMessageCodecTest`, `TransactionEventMessageTest`): read an insert, an update and a delete in the shape Debezium publishes (including envelope fields the service ignores), reject malformed payloads and unknown enum values, and check the JSON of a transaction event.
- The flow was checked end to end against the Docker Kafka, Kafka Connect and Postgres: `POST` saved a `PENDING` row and answered 202, the translator published `TRANSACTION_CREATED`, status changes made in the database produced `TRANSACTION_PROCESSING`, `TRANSACTION_COMPLETED` and `TRANSACTION_FAILED` in the update topic, a `PUT` that only changed the amount produced no event, and nothing reached the DLQs. There are no automated integration tests yet.

## Trade-offs and known limitations

- More classes than a simple Spring CRUD (controller, service, repository). The gain is isolation and testability; the cost is mapping code. For a very small project this can feel heavy.
- One service per entity was chosen over one class per operation (use case) to reduce the number of files. The trade-off is that a service can grow large and accumulate dependencies as business logic increases.
- Update is done in two repository calls (read, then save), not in a single database transaction. For a plain CRUD this is acceptable; for logic that needs atomicity, add a transaction boundary.
- Transactions here are fully editable and deletable because CRUD was requested. A real ledger would normally be append-only (corrections are new entries).
- A new transaction is visible right after the 202, as `PENDING`. The validator then moves it to `PROCESSING` or `FAILED`, and the movement recorder moves a `PROCESSING` one to `COMPLETED` once its movement is saved. The transaction is therefore briefly `PROCESSING` without a movement, and one that fails in the recorder stays `PROCESSING` in the dead letter queue. Nothing notifies the client of the outcome: that is the next step. The allowed moves are in `TransactionStatus.canMoveTo`, but only the validator and the recorder use them so far: `update` through the API and a direct change in the database can still set any status.
- A `FAILED` transaction records no reason, and a message that reaches a dead letter queue leaves its transaction `PENDING` with nothing to replay it or time it out.
- Consumers never see a `PUT` that does not change the status, nor a delete, because the translator produces no event for them.
- There is no idempotency key from the client: a client that retries a `POST` after a timeout creates a second transaction.
- Delivery is at-least-once, so the same event can be published twice. Its id is the same each time, so a consumer can ignore a duplicate. Order is kept inside each event topic but not between the two. A message in a DLQ needs a human or a job to look at it: there is no automatic reprocessing yet.
- Paging is offset-based (`page` and `size`, size capped by `app.pagination.max-page-size`).
- Identifiers are random UUIDs generated by the service.
- A request for a user that is not `ACTIVE` is accepted (`202`) and the transaction fails a moment later, during the validation. The client only finds out by reading the transaction (`FAILED`), and nothing says why. The status is checked once, at the validation, so blocking a user afterwards does not stop a transaction that is already `PROCESSING`.
- `PUT /users/{id}/status` has no authentication and keeps no history of who changed a status or when, so anyone who can reach the API can block a user.
- The cached user status is read by the validator, which trusts what it finds, and `POST /transactions` does not look at the status. The cached value can be missing (Redis was down when the status changed, or the entry expired) or briefly different from the database, so a reader must treat a missing entry as a miss and fall back to the database. See [ADR 0007](adr/0007-cache-the-status-of-users-in-redis.md).
- `POST /users` has no authentication, so anyone can register. It answers 409 for an email that is already registered, which tells a caller that the email exists.
- The user is checked and then the transaction is saved, in two steps. A user cannot be deleted through the API today, so nothing can remove the user in between, and the foreign key is the last line of defence.
- A transaction cannot change owner: the owner is set at creation and `PUT` keeps it.
