# Architecture

The project follows **Clean Architecture**: the code is organized in layers, and dependencies only point **inward**, from the frameworks toward the business rules. The business rules never depend on Spring, JPA or HTTP.

## The dependency rule

```
adapter.web ──┐
              ├──> application ──> domain
adapter.persistence ──┘
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
| `application.click.ClickService` | `create`, `get`, `list`, `update`, `delete` for clicks |
| `application.transaction.TransactionService` | The same five operations for transactions |
| `application.shared.NotFoundException` | Thrown when a resource does not exist |

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
| `ApiExceptionHandler` | Maps `NotFoundException` to 404 and `DomainValidationException` to 400 |

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

### `config`: frameworks and wiring

`ServiceConfig` is the **composition root**: it creates each service and injects the repository port. Spring picks the adapter that implements the port. This is the only place that knows both sides, and it keeps the application layer free of Spring.

## How a request flows

`POST /clicks`:

1. `ClickController` receives the JSON as a `ClickRequest` and validates its format.
2. It calls `ClickService.create(adId, userId, clickedAt)`.
3. The service creates a `Click` (the domain validates its rules) and calls `ClickRepository.save`.
4. `ClickRepositoryAdapter` converts the `Click` to a `ClickJpaEntity` and saves it through Spring Data.
5. The result goes back as a `Click`, then a `ClickResponse`, then JSON with status 201.

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

The values are bound to the `PaginationProperties` record (`@ConfigurationProperties`), which also keeps the clamping logic in one place instead of duplicating it in each controller. The controller base paths (`/clicks`, `/transactions`) are written directly in `@RequestMapping`. The `Location` header of a created resource is built from the current request URL.

## Testing strategy

- **Domain tests** (`ClickTest`, `TransactionTest`): pure Java, no Spring, no database.
- **Mapper tests** (`ClickMapperTest`, `TransactionMapperTest`): check that the conversion in both directions loses nothing.
- **Service tests** (`ClickServiceTest`): use `InMemoryClickRepository`, a test double that implements the port. This works because services depend on the interface, not on JPA.
- Full-stack behavior was checked by running the application against the Docker Postgres.

## Trade-offs and known limitations

- More classes than a simple Spring CRUD (controller, service, repository). The gain is isolation and testability; the cost is mapping code. For a very small project this can feel heavy.
- One service per entity was chosen over one class per operation (use case) to reduce the number of files. The trade-off is that a service can grow large and accumulate dependencies as business logic increases.
- Update is done in two repository calls (check existence, then save), not in a single database transaction. For a plain CRUD this is acceptable; for logic that needs atomicity, add a transaction boundary.
- Transactions here are fully editable and deletable because CRUD was requested. A real ledger would normally be append-only (corrections are new entries).
- Paging is offset-based (`page` and `size`, size capped by `app.pagination.max-page-size`).
- Identifiers are random UUIDs generated by the service.
