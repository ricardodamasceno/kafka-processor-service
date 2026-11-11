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
| `domain.transaction.TransactionStatus` | Enum: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`. Where a transaction is in its life |
| `domain.transaction.TransactionRepository` | Port: the contract for storing transactions. Defined here, implemented elsewhere |
| `domain.user.User` | Entity: an immutable record. The name is trimmed and the email is trimmed and lower-cased, so two emails that differ only in case are the same user. Limits: name up to 200 characters, email up to 320 and in a valid format |
| `domain.user.UserRepository` | Port: the contract for storing users and checking whether an email is taken |
| `domain.user.UserStatus` | Enum: `ACTIVE`, `INACTIVE`, `BLOCKED`. Where a user is in their life. A new user is `ACTIVE` |
| `domain.shared.DomainValidationException` | Thrown when a domain rule is violated |

Why: if a rule such as "an amount must be positive" lives in the entity, it is enforced no matter who creates a `Transaction` (an HTTP call, a Kafka consumer, a test).

### `application`: application business rules (services)

What lives here: the operations the system supports. Each service orchestrates domain objects and calls ports.

| Class | Role |
|---|---|
| `application.transaction.TransactionService` | `create`, `get`, `list`, `update`, `delete` for transactions. `create` checks that the user exists and is `ACTIVE`, through the `UserRepository` port |
| `application.transaction.TransactionEvent` | Something that happened to a transaction: a type, the complete state of the transaction, and an id that stays the same if the event is produced twice |
| `application.transaction.TransactionEventType` | `TRANSACTION_CREATED`, `TRANSACTION_PROCESSING`, `TRANSACTION_COMPLETED`, `TRANSACTION_FAILED` |
| `application.transaction.TransactionEventPublisher` | Outbound port: how the application announces a transaction event. Implemented by the Kafka adapter |
| `application.user.UserService` | `register`: validates, assigns an id and the registration time (from the `Clock`), refuses an email that is already registered, and saves the user as `ACTIVE` |
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
| `UserController` | `POST /users`: registers a user |
| `UserRequest` | Input DTO. It only checks that the fields are present; format and lengths are rules of the domain `User` |
| `UserResponse` | Output DTO |
| `ApiExceptionHandler` | Maps `NotFoundException` to 404, `ConflictException` to 409 and `DomainValidationException` to 400 |

DTOs are separate from domain entities so the API contract and the domain model can change independently.

**`adapter.persistence`** (the driven side: we call something)

| Class | Role |
|---|---|
| `TransactionJpaEntity` | JPA class that maps to the table. It is **not** the domain entity |
| `TransactionJpaRepository` | Spring Data interface (Spring generates the SQL access) |
| `TransactionRepositoryAdapter` | Implements the domain port and delegates to Spring Data; it does the persistence only |
| `UserJpaEntity`, `UserJpaRepository`, `UserMapper` | The same three pieces for the `users` table. The repository checks the email with `lower()`, which is what the unique index covers |
| `UserRepositoryAdapter` | Implements `UserRepository`. When the unique index on the email rejects an insert, it throws `ConflictException` |
| `TransactionMapper` | Converts between domain entity and JPA entity (`toEntity` / `toDomain`). Its only responsibility is this translation. It is a stateless utility class with static methods, not a Spring bean |

Why two entity classes: JPA needs a no-arg constructor, annotations and a mutable shape. Keeping that out of the domain keeps the domain pure. The cost is the mapping step, which lives in the mapper classes so it can be tested on its own.

Why `TransactionJpaRepository` needs no `@Repository`: it is only an interface. Spring Data JPA scans for interfaces that extend `JpaRepository` and creates the implementation at runtime (a proxy backed by `SimpleJpaRepository`, which uses the JPA `EntityManager` and Hibernate).

**`adapter.messaging`** (Kafka: both driving and driven)

The package `cdc` is the only part that knows how the changes are captured. Everything else works with transaction events.

| Class | Role |
|---|---|
| `cdc.TransactionChangeConsumer` | `@KafkaListener` class (the **translator**, the only reader of `transactions-cdc-events`). Reads each change, asks the translator for an event and publishes it |
| `cdc.TransactionChangeTranslator` | The rules that turn a row change into a `TransactionEvent`, or into nothing. The only class that interprets `before` and `after` |
| `cdc.TransactionChangeMessage` | The JSON shape of a change as Debezium publishes it (`op`, `before`, `after`). The row fields use the column names, so the table schema is the contract of that topic |
| `transaction.TransactionEventKafkaPublisher` | Implements the event publisher port (the **producer**). Key = transaction id. `TRANSACTION_CREATED` goes to `payments-transactions-create`, the other events to `payments-transactions-update` |
| `transaction.TransactionEventMessage` | The JSON shape of a transaction event: the contract of those topics, separate from the domain class |
| `KafkaMessageSender` | Sends a message and waits for the broker acknowledgement (`acks=all`) |
| `JsonMessageCodec` | Converts messages to and from JSON text. Messages are plain JSON strings on the topic |
| `KafkaErrorHandlingConfig` | Retries a failed message, then sends it to the dead letter queue (DLQ). Bad payloads and broken domain rules skip the retries |
| `MessagingProperties` | Topic names, send timeout and retry policy, read from `application.yml` |

Consumer group: `transaction-event-translator`. All instances of the application in a group share the partitions.

### `config`: frameworks and wiring

`ServiceConfig` is the **composition root**: it creates each service and injects the ports (the repository, and the `Clock` when a service needs the current time). Spring picks the adapter that implements each port. This is the only place that knows both sides, and it keeps the application layer free of Spring.

## How a request flows

`POST /transactions`:

1. `TransactionController` receives the JSON as a `TransactionRequest` and validates its format.
2. It calls `TransactionService.create(userId, accountId, type, amount, occurredAt)`.
3. The service creates a `Transaction` with an id and the status `PENDING`. The domain validates its rules first, so invalid data is a **400** whatever the user.
4. The service looks for the user through the `UserRepository` port. If the user does not exist it throws `NotFoundException` (**404**). If the user is not `ACTIVE` it throws `ConflictException` (**409**), and nothing is saved.
5. `TransactionRepositoryAdapter` converts the `Transaction` to a `TransactionJpaEntity` and saves it.
6. The API answers **202 Accepted** with an empty body (no id and no `Location` header). The row is already in the database, so `GET /transactions/{id}` returns 200 straight away, with the status `PENDING`. Since the response does not carry the id, a client finds the record with `GET /transactions` (most recent first).
7. Debezium reads the insert from the PostgreSQL write-ahead log and publishes the change to `transactions-cdc-events`.
8. `TransactionChangeConsumer` reads the change, `TransactionChangeTranslator` turns it into a `TRANSACTION_CREATED` event (the complete state of the transaction, including the `userId`, with the event id `<transactionId>:PENDING`), and `TransactionEventKafkaPublisher` publishes it to `payments-transactions-create` with the transaction id as key. Nothing reads that topic yet, so nothing moves the transaction past `PENDING`.

When the status changes later (`PROCESSING`, `COMPLETED` or `FAILED`), the same path publishes the matching event to `payments-transactions-update`. A change that does not alter the status, such as a `PUT` that only changes the amount, and a delete produce no event.

`GET`, `PUT` and `DELETE` go straight to the database. Every change to the table, including these, is published to `transactions-cdc-events`. `PUT` keeps the current owner and status, because the request does not carry them: a `userId` sent in a `PUT` body is ignored.

`POST /users`:

1. `UserController` receives the JSON as a `UserRequest` and checks that `name` and `email` are present.
2. It calls `UserService.register(name, email)`.
3. The service creates a `User` (the domain trims and normalizes the data and checks its rules; a broken rule is a 400 with the reason) with a new id, the time from the `Clock` and the status `ACTIVE`. The status is not part of the request: a client cannot choose it, and a `status` field in the body is ignored.
4. If the email is already registered, regardless of case, the service throws `ConflictException`, which is a **409**.
5. `UserRepositoryAdapter` converts the `User` to a `UserJpaEntity` and saves it. If two requests with the same email pass step 4 at the same time, the unique index rejects the second one and the adapter reports it as the same `ConflictException`.
6. The API answers **201 Created** with the user (`id`, `name`, `email`, `createdAt`, `status`). Unlike creating a transaction, the body carries the id, because registration is synchronous and the client needs the id to create transactions. There is no `Location` header.

```bash
curl -i -X POST http://localhost:8080/users -H "Content-Type: application/json" -d '{"name":"Alice Silva","email":"alice@example.com"}'
```

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
The Kafka and DLQ settings live under `app.messaging` (`send-timeout`, `retry.max-retries`, `retry.interval`, `topics.*`), bound to `MessagingProperties`. The connection settings (`bootstrap-servers`, `acks=all`, idempotent producer, `auto-offset-reset: earliest`, manual commit per record) are under `spring.kafka`. The Redis connection (`host`, `port` and the timeouts) is under `spring.data.redis`; see [redis.md](redis.md).

## Testing strategy

- **Domain tests** (`TransactionTest`): pure Java, no Spring, no database.
- **Mapper tests** (`TransactionMapperTest`): check that the conversion in both directions loses nothing.
- **Service tests** (`TransactionServiceTest`): use `InMemoryTransactionRepository`, a test double that implements the port. This works because services depend on the interface, not on JPA. `TransactionServiceTest` also uses `InMemoryUserRepository`. They cover: a created transaction is `PENDING` and owned by the user, an unknown user and a user that is `INACTIVE` or `BLOCKED` store nothing, invalid data is rejected before the user is looked up, `update` keeps the owner and the status, `delete`, and paging.
- **User tests** (`UserTest`, `UserServiceTest`, `UserMapperTest`): normalization, the name and email rules, a duplicated email regardless of case, nothing stored when the data is invalid, the registration time coming from a fixed `Clock`, and a new user being `ACTIVE`.
- **Event tests** (`TransactionEventTest`): the event type follows the status, and the event id is the same for the same transaction and status.
- **Translator tests** (`TransactionChangeTranslatorTest`): every rule of the translation table, plus the rejections: an update without the old row, a change without an operation, an incomplete row, and a row that breaks a domain rule.
- **Message contract tests** (`JsonMessageCodecTest`, `TransactionEventMessageTest`): read an insert, an update and a delete in the shape Debezium publishes (including envelope fields the service ignores), reject malformed payloads and unknown enum values, and check the JSON of a transaction event.
- The flow was checked end to end against the Docker Kafka, Kafka Connect and Postgres: `POST` saved a `PENDING` row and answered 202, the translator published `TRANSACTION_CREATED`, status changes made in the database produced `TRANSACTION_PROCESSING`, `TRANSACTION_COMPLETED` and `TRANSACTION_FAILED` in the update topic, a `PUT` that only changed the amount produced no event, and nothing reached the DLQs. There are no automated integration tests yet.

## Trade-offs and known limitations

- More classes than a simple Spring CRUD (controller, service, repository). The gain is isolation and testability; the cost is mapping code. For a very small project this can feel heavy.
- One service per entity was chosen over one class per operation (use case) to reduce the number of files. The trade-off is that a service can grow large and accumulate dependencies as business logic increases.
- Update is done in two repository calls (read, then save), not in a single database transaction. For a plain CRUD this is acceptable; for logic that needs atomicity, add a transaction boundary.
- Transactions here are fully editable and deletable because CRUD was requested. A real ledger would normally be append-only (corrections are new entries).
- A new transaction is visible right after the 202, as `PENDING`. Nothing moves it past `PENDING` yet: the executor that reads `payments-transactions-create` and the notifier that reads `payments-transactions-update` are the next steps. There is no state machine either, so nothing stops an invalid status change.
- Consumers never see a `PUT` that does not change the status, nor a delete, because the translator produces no event for them.
- There is no idempotency key from the client: a client that retries a `POST` after a timeout creates a second transaction.
- Delivery is at-least-once, so the same event can be published twice. Its id is the same each time, so a consumer can ignore a duplicate. Order is kept inside each event topic but not between the two. A message in a DLQ needs a human or a job to look at it: there is no automatic reprocessing yet.
- Paging is offset-based (`page` and `size`, size capped by `app.pagination.max-page-size`).
- Identifiers are random UUIDs generated by the service.
- Only an `ACTIVE` user can create a transaction, but nothing changes a user's status yet: there is no endpoint to block or deactivate a user. The status is checked only when a transaction is created, so blocking a user does not stop the transactions that are already `PENDING`.
- `POST /users` has no authentication, so anyone can register. It answers 409 for an email that is already registered, which tells a caller that the email exists.
- The user is checked and then the transaction is saved, in two steps. A user cannot be deleted through the API today, so nothing can remove the user in between, and the foreign key is the last line of defence.
- A transaction cannot change owner: the owner is set at creation and `PUT` keeps it.
