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
| `domain.click.Click` | Entity: an immutable record that validates itself in the constructor |
| `domain.click.ClickRepository` | Port: the contract for storing clicks. Defined here, implemented elsewhere |
| `domain.transaction.Transaction` | Entity: money is `BigDecimal`, amount is always positive with at most 2 decimals |
| `domain.transaction.TransactionType` | Enum: `DEPOSIT`, `WITHDRAWAL` |
| `domain.transaction.TransactionRepository` | Port for storing transactions |
| `domain.shared.DomainValidationException` | Thrown when a domain rule is violated |

Why: if a rule such as "an amount must be positive" lives in the entity, it is enforced no matter who creates a `Transaction` (an HTTP call, a Kafka consumer, a test).

### `application`: application business rules (services)

What lives here: the operations the system supports. Each service orchestrates domain objects and calls ports.

| Class | Role |
|---|---|
| `application.click.ClickService` | `requestCreation`, `persist`, `get`, `list`, `update`, `delete` for clicks |
| `application.transaction.TransactionService` | The same operations for transactions |
| `application.click.ClickEventPublisher`, `application.transaction.TransactionEventPublisher` | Outbound ports: how the application announces events. Implemented by the Kafka adapter |
| `application.shared.NotFoundException` | Thrown when a resource does not exist |
| `application.shared.PublishFailedException` | Thrown when an event could not be published |

Creation is asynchronous: `requestCreation` validates, assigns an id and publishes an event (it writes nothing to the database); `persist` is called by the Kafka consumer and writes the record. `persist` is idempotent because the id comes with the event. See [ADR 0002](adr/0002-asynchronous-creation-with-kafka.md).

Why a service per entity: it keeps the number of files small for a simple CRUD. A service takes plain values as parameters, so it does not depend on the HTTP JSON format. If a service grows too large, or an operation gets rich business logic (for example a withdrawal), that operation can be extracted into its own use case class.

The services have no Spring annotations on purpose (see `config`).

### `adapter`: interface adapters

Adapters convert data between the format the inner layers use and the format an external technology uses.

**`adapter.web`** (the driving side: something calls us)

| Class | Role |
|---|---|
| `ClickController`, `TransactionController` | Receive HTTP, call a service, return HTTP. No business rule |
| `ClickRequest`, `TransactionRequest` | Input DTOs (data transfer objects) with Bean Validation (`@NotBlank`, `@Positive`...) |
| `ClickResponse`, `TransactionResponse` | Output DTOs |
| `ApiExceptionHandler` | Maps `NotFoundException` to 404, `DomainValidationException` to 400 and `PublishFailedException` to 503 |

DTOs are separate from domain entities so the API contract and the domain model can change independently.

**`adapter.persistence`** (the driven side: we call something)

| Class | Role |
|---|---|
| `ClickJpaEntity`, `TransactionJpaEntity` | JPA classes that map to the tables. They are **not** the domain entities |
| `ClickJpaRepository`, `TransactionJpaRepository` | Spring Data interfaces (Spring generates the SQL access) |
| `ClickRepositoryAdapter`, `TransactionRepositoryAdapter` | Implement the domain ports and delegate to Spring Data; they do the persistence only |
| `ClickMapper`, `TransactionMapper` | Convert between domain entity and JPA entity (`toEntity` / `toDomain`). Their only responsibility is this translation. They are stateless utility classes with static methods, not Spring beans |

Why two entity classes: JPA needs a no-arg constructor, annotations and a mutable shape. Keeping that out of the domain keeps the domain pure. The cost is the mapping step, which lives in the mapper classes so it can be tested on its own.

Why `ClickJpaRepository` needs no `@Repository`: it is only an interface. Spring Data JPA scans for interfaces that extend `JpaRepository` and creates the implementation at runtime (a proxy backed by `SimpleJpaRepository`, which uses the JPA `EntityManager` and Hibernate).

**`adapter.messaging`** (Kafka: both driving and driven)

| Class | Role |
|---|---|
| `ClickKafkaPublisher`, `TransactionKafkaPublisher` | Implement the event publisher ports (the **producers**). Key = entity id |
| `ClickKafkaConsumer`, `TransactionKafkaConsumer` | `@KafkaListener` classes (the **consumers**). Read the JSON, rebuild the domain object and call `persist` |
| `ClickMessage`, `TransactionMessage` | The JSON shape on the topic: the contract between producer and consumer, separate from the domain classes |
| `ClickMessageMapper`, `TransactionMessageMapper` | Static converters between domain and message |
| `KafkaMessageSender` | Sends a message and waits for the broker acknowledgement (`acks=all`) |
| `JsonMessageCodec` | Converts messages to and from JSON text. Messages are plain JSON strings on the topic |
| `KafkaErrorHandlingConfig` | Retries a failed message, then sends it to the dead letter queue (DLQ). Bad payloads and broken domain rules skip the retries |
| `MessagingProperties` | Topic names, send timeout and retry policy, read from `application.yml` |

Consumer groups: `click-persister` and `transaction-persister`. All instances of the application in a group share the partitions.

### `config`: frameworks and wiring

`ServiceConfig` is the **composition root**: it creates each service and injects the ports (repository and event publisher). Spring picks the adapter that implements each port. This is the only place that knows both sides, and it keeps the application layer free of Spring.

## How a request flows

`POST /clicks` (asynchronous creation):

1. `ClickController` receives the JSON as a `ClickRequest` and validates its format.
2. It calls `ClickService.requestCreation(adId, userId, clickedAt)`.
3. The service creates a `Click` (the domain validates its rules and an id is generated) and calls `ClickEventPublisher`.
4. `ClickKafkaPublisher` serializes a `ClickMessage` and sends it to the `ad.clicks` topic with the id as key. It waits for the broker acknowledgement.
5. The API answers **202 Accepted** with an empty body (no id and no `Location` header). Nothing is in the database yet.
6. Later, `ClickKafkaConsumer` reads the message, rebuilds the `Click` and calls `ClickService.persist`.
7. `ClickRepositoryAdapter` converts the `Click` to a `ClickJpaEntity` and saves it. From now on `GET /clicks/{id}` returns 200 (before that it returns 404). Since the response does not carry the id, a client finds the record with `GET /clicks` (most recent first).

Transactions follow the same path through `payments.transfers`. `GET`, `PUT` and `DELETE` are still synchronous and go straight to the database.

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

The values are bound to the `PaginationProperties` record (`@ConfigurationProperties`), which also keeps the clamping logic in one place instead of duplicating it in each controller. The controller base paths (`/clicks`, `/transactions`) are written directly in `@RequestMapping`.
The Kafka and DLQ settings live under `app.messaging` (`send-timeout`, `retry.max-retries`, `retry.interval`, `topics.*`), bound to `MessagingProperties`. The connection settings (`bootstrap-servers`, `acks=all`, idempotent producer, `auto-offset-reset: earliest`, manual commit per record) are under `spring.kafka`.

## Testing strategy

- **Domain tests** (`ClickTest`, `TransactionTest`): pure Java, no Spring, no database.
- **Mapper tests** (`ClickMapperTest`, `TransactionMapperTest`): check that the conversion in both directions loses nothing.
- **Service tests** (`ClickServiceTest`): use `InMemoryClickRepository` and `RecordingClickEventPublisher`, test doubles that implement the ports. This works because services depend on the interfaces, not on JPA or Kafka. They cover: the event is published but nothing is written, invalid data is rejected before publishing, a broker failure surfaces, and `persist` is idempotent.
- **Message contract tests** (`JsonMessageCodecTest`): JSON round trip of both messages, and rejection of malformed payloads.
- The asynchronous flow was checked end to end against the Docker Kafka and Postgres: create, consume, read, a duplicated event (one row), and bad payloads (sent to the DLQ). There are no automated integration tests yet.

## Trade-offs and known limitations

- More classes than a simple Spring CRUD (controller, service, repository). The gain is isolation and testability; the cost is mapping code. For a very small project this can feel heavy.
- One service per entity was chosen over one class per operation (use case) to reduce the number of files. The trade-off is that a service can grow large and accumulate dependencies as business logic increases.
- Update is done in two repository calls (check existence, then save), not in a single database transaction. For a plain CRUD this is acceptable; for logic that needs atomicity, add a transaction boundary.
- Transactions here are fully editable and deletable because CRUD was requested. A real ledger would normally be append-only (corrections are new entries).
- Creation is eventually consistent: right after a 202, `GET` returns 404 until the consumer persists the record, and an `update` or `delete` sent too early also returns 404. Update and delete are still synchronous.
- Delivery is at-least-once. Duplicates are harmless because the id is generated before publishing and `persist` overwrites the same row. A message in the DLQ needs a human or a job to look at it: there is no automatic reprocessing yet.
- Paging is offset-based (`page` and `size`, size capped by `app.pagination.max-page-size`).
- Identifiers are random UUIDs generated by the service.
