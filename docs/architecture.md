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
| `domain.transaction.Transaction` | Entity: an immutable record that validates itself in the constructor. Money is `BigDecimal`, amount is always positive with at most 2 decimals |
| `domain.transaction.TransactionType` | Enum: `DEPOSIT`, `WITHDRAWAL` |
| `domain.transaction.TransactionStatus` | Enum: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`. Where a transaction is in its life |
| `domain.transaction.TransactionRepository` | Port: the contract for storing transactions. Defined here, implemented elsewhere |
| `domain.shared.DomainValidationException` | Thrown when a domain rule is violated |

Why: if a rule such as "an amount must be positive" lives in the entity, it is enforced no matter who creates a `Transaction` (an HTTP call, a Kafka consumer, a test).

### `application`: application business rules (services)

What lives here: the operations the system supports. Each service orchestrates domain objects and calls ports.

| Class | Role |
|---|---|
| `application.transaction.TransactionService` | `create`, `get`, `list`, `update`, `delete` for transactions |
| `application.shared.NotFoundException` | Thrown when a resource does not exist |

`create` validates, assigns an id and saves the transaction as `PENDING`. What happens next is driven by the changes of the `transactions` table, which Debezium publishes to Kafka: the application never calls Kafka to announce an event. See [ADR 0005](adr/0005-pending-transactions-and-cdc-events.md) and [debezium.md](debezium.md).

Why a service per entity: it keeps the number of files small for a simple CRUD. A service takes plain values as parameters, so it does not depend on the HTTP JSON format. If a service grows too large, or an operation gets rich business logic (for example a withdrawal), that operation can be extracted into its own use case class.

The services have no Spring annotations on purpose (see `config`).

### `adapter`: interface adapters

Adapters convert data between the format the inner layers use and the format an external technology uses.

**`adapter.web`** (the driving side: something calls us)

| Class | Role |
|---|---|
| `TransactionController` | Receives HTTP, calls a service, returns HTTP. No business rule |
| `TransactionRequest` | Input DTO (data transfer object) with Bean Validation (`@NotBlank`, `@Positive`...) |
| `TransactionResponse` | Output DTO |
| `ApiExceptionHandler` | Maps `NotFoundException` to 404 and `DomainValidationException` to 400 |

DTOs are separate from domain entities so the API contract and the domain model can change independently.

**`adapter.persistence`** (the driven side: we call something)

| Class | Role |
|---|---|
| `TransactionJpaEntity` | JPA class that maps to the table. It is **not** the domain entity |
| `TransactionJpaRepository` | Spring Data interface (Spring generates the SQL access) |
| `TransactionRepositoryAdapter` | Implements the domain port and delegates to Spring Data; it does the persistence only |
| `TransactionMapper` | Converts between domain entity and JPA entity (`toEntity` / `toDomain`). Its only responsibility is this translation. It is a stateless utility class with static methods, not a Spring bean |

Why two entity classes: JPA needs a no-arg constructor, annotations and a mutable shape. Keeping that out of the domain keeps the domain pure. The cost is the mapping step, which lives in the mapper classes so it can be tested on its own.

Why `TransactionJpaRepository` needs no `@Repository`: it is only an interface. Spring Data JPA scans for interfaces that extend `JpaRepository` and creates the implementation at runtime (a proxy backed by `SimpleJpaRepository`, which uses the JPA `EntityManager` and Hibernate).

**`adapter.messaging`** (Kafka: the application reads the changes of the `transactions` table)

| Class | Role |
|---|---|
| `TransactionChangeConsumer` | `@KafkaListener` class (the **consumer**) of `transactions-cdc-events`. Reads each change and, for now, only logs it |
| `TransactionChangeMessage` | The JSON shape of a change as Debezium publishes it (`op`, `before`, `after`). The row fields use the column names, so the table schema is the contract of the topic |
| `JsonMessageCodec` | Reads messages from JSON text. Messages are plain JSON strings on the topic |
| `KafkaErrorHandlingConfig` | Retries a failed message, then sends it to the dead letter queue (DLQ). Bad payloads and broken domain rules skip the retries |
| `MessagingProperties` | Topic names and retry policy, read from `application.yml` |

Consumer group: `transaction-change-logger`. All instances of the application in a group share the partitions.

### `config`: frameworks and wiring

`ServiceConfig` is the **composition root**: it creates each service and injects the ports (the repository). Spring picks the adapter that implements each port. This is the only place that knows both sides, and it keeps the application layer free of Spring.

## How a request flows

`POST /transactions`:

1. `TransactionController` receives the JSON as a `TransactionRequest` and validates its format.
2. It calls `TransactionService.create(accountId, type, amount, occurredAt)`.
3. The service creates a `Transaction` with an id and the status `PENDING` (the domain validates its rules) and saves it through the `TransactionRepository` port.
4. `TransactionRepositoryAdapter` converts the `Transaction` to a `TransactionJpaEntity` and saves it.
5. The API answers **202 Accepted** with an empty body (no id and no `Location` header). The row is already in the database, so `GET /transactions/{id}` returns 200 straight away, with the status `PENDING`. Since the response does not carry the id, a client finds the record with `GET /transactions` (most recent first).
6. Debezium reads the insert from the PostgreSQL write-ahead log and publishes the change to `transactions-cdc-events`.
7. `TransactionChangeConsumer` reads the change and logs it. Nothing moves the transaction past `PENDING` yet.

`GET`, `PUT` and `DELETE` go straight to the database. Every change to the table, including these, is published to `transactions-cdc-events`. `PUT` keeps the current status, because the request does not carry it.

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
The Kafka and DLQ settings live under `app.messaging` (`retry.max-retries`, `retry.interval`, `topics.*`), bound to `MessagingProperties`. The connection settings (`bootstrap-servers`, `acks=all`, idempotent producer, `auto-offset-reset: earliest`, manual commit per record) are under `spring.kafka`.

## Testing strategy

- **Domain tests** (`TransactionTest`): pure Java, no Spring, no database.
- **Mapper tests** (`TransactionMapperTest`): check that the conversion in both directions loses nothing.
- **Service tests** (`TransactionServiceTest`): use `InMemoryTransactionRepository`, a test double that implements the port. This works because services depend on the interface, not on JPA. They cover: a created transaction is `PENDING`, invalid data stores nothing, `update` keeps the status, `delete`, and paging.
- **Message contract tests** (`JsonMessageCodecTest`): read an insert, an update and a delete in the shape Debezium publishes (including envelope fields the service ignores), and reject malformed payloads and unknown enum values.
- The flow was checked end to end against the Docker Kafka, Kafka Connect and Postgres, using a scratch database: `POST` saved a `PENDING` row and answered 202, an invalid `POST` answered 400, `PUT` kept the status, and the insert, both updates and the delete reached `transactions-cdc-events` and the consumer, with nothing in the DLQ. There are no automated integration tests yet.

## Trade-offs and known limitations

- More classes than a simple Spring CRUD (controller, service, repository). The gain is isolation and testability; the cost is mapping code. For a very small project this can feel heavy.
- One service per entity was chosen over one class per operation (use case) to reduce the number of files. The trade-off is that a service can grow large and accumulate dependencies as business logic increases.
- Update is done in two repository calls (read, then save), not in a single database transaction. For a plain CRUD this is acceptable; for logic that needs atomicity, add a transaction boundary.
- Transactions here are fully editable and deletable because CRUD was requested. A real ledger would normally be append-only (corrections are new entries).
- A new transaction is visible right after the 202, as `PENDING`. Nothing moves it past `PENDING` yet (executing the payment is the next step), and there is no state machine, so nothing stops an invalid status change.
- There is no idempotency key from the client: a client that retries a `POST` after a timeout creates a second transaction.
- Delivery of the change events is at-least-once, so a consumer must tolerate seeing the same change twice. A message in the DLQ needs a human or a job to look at it: there is no automatic reprocessing yet.
- Paging is offset-based (`page` and `size`, size capped by `app.pagination.max-page-size`).
- Identifiers are random UUIDs generated by the service.
