# Database (PostgreSQL) and migrations (Flyway)

## Local PostgreSQL

Defined in `docker/docker-compose.yml`, service `postgres`.

| Item | Default value |
|---|---|
| Image | `postgres:17` |
| Port | 5432 |
| Database | `events_processor` |
| User / password | `events` / `events` |
| Data | named volume `postgres-data` (survives `down`, removed by `down -v`) |
| Server setting | `wal_level=logical`, set in the Compose `command`. Debezium reads the write-ahead log (see [debezium.md](debezium.md)) |

The values can be changed with the `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` variables when starting Compose. Credentials are for local use only.

Start only the database:

```bash
docker compose -f docker/docker-compose.yml up -d postgres
```

Open a SQL console:

```bash
docker exec -it postgres psql -U events -d events_processor
```

## Migrations with Flyway

Configured in `build.gradle` with the `org.flywaydb.flyway` plugin. Migrations live in `src/main/resources/db/migration`.

**Migrations never run on their own.** Nothing ties them to application startup or to the `build`, `test` or `run` tasks. They run only when you execute the command below.

Apply pending migrations:

```bash
./gradlew flywayMigrate
```

Show the state (applied, pending):

```bash
./gradlew flywayInfo
```

Validate that the files match what was applied:

```bash
./gradlew flywayValidate
```

### Creating a migration

Create a file in `src/main/resources/db/migration` named `V<version>__<description>.sql`, for example `V1__create_transactions.sql` (two underscores after the version). Rules:

- Versions grow in sequence (`V1`, `V2`, ...).
- An already applied migration **must not be edited**: Flyway stores its checksum and `flywayValidate` fails if the file changes. To fix something, create a new migration.
- For repeatable changes (views, functions), use the `R__` prefix.

### Connection

Default values point to the Compose Postgres. For another database, set environment variables before the command:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/events_processor` |
| `DB_USER` | `events` |
| `DB_PASSWORD` | `events` |

```bash
DB_URL=jdbc:postgresql://localhost:5432/other_db ./gradlew flywayMigrate
```

### Starting over (local only)

Deletes all data in the database and the Flyway history:

```bash
docker compose -f docker/docker-compose.yml down postgres -v
```

Then start the database again and run `flywayMigrate`.

To recreate only the application database, keeping the container and the volume:

```bash
docker exec postgres psql -U events -d postgres -c "DROP DATABASE events_processor WITH (FORCE);" -c "CREATE DATABASE events_processor;"
```

## Current migrations

| Version | File | Creates |
|---|---|---|
| V1 | `V1__add_transaction.sql` | Table `transactions` (id, account_id, type, amount, occurred_at, status), checks on `type`, `amount > 0` and `status` (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, no default), an index on `(account_id, occurred_at)`, and `REPLICA IDENTITY FULL` so the change events published by Debezium carry the complete old row. See [debezium.md](debezium.md) |
| V2 | `V2__add_user.sql` | Table `users` (id, name, email, created_at). The email is unique regardless of case, through a unique index on `lower(email)`, so the application should store it normalized |
| V3 | `V3__add_user_id_to_transaction.sql` | Adds `user_id` to `transactions`, a foreign key to `users`, and an index on `(user_id, occurred_at)`. The column is `NOT NULL` with no default, so the migration only runs while `transactions` is empty and every insert has to state the user. A user that has transactions cannot be deleted |
| V4 | `V4__add_user_status.sql` | Adds `status` to `users` (`ACTIVE`, `INACTIVE`, `BLOCKED`). Users that already existed become `ACTIVE`, and the default is dropped afterwards so every insert states its status |
| V5 | `V5__add_movement.sql` | Table `movements` (id, user_id, transaction_id, amount, type, created_at). `type` is `CREDIT` or `DEBIT` and the amount is always positive. `user_id` and `transaction_id` are foreign keys. A unique index on `(transaction_id, type)` lets a transaction have at most one movement of each type, so a repeated delivery of the event that triggers a movement cannot move the money twice. An index on `(user_id, created_at)` serves the movements of a user over time. The table has `REPLICA IDENTITY FULL`, because it is captured by Debezium |

The history was rewritten on 2026-11-10: the local database was recreated, so the migrations of the removed `clicks` table were dropped and the others were merged into a single `V1`. That is only possible because nothing outside the local environment had applied them. Once a migration has run anywhere else, it must not be edited or renumbered, and a change goes into a new migration.

Timestamps use `TIMESTAMPTZ` and money uses `NUMERIC(19, 2)`.

The Spring Boot application never runs these migrations. Its Hibernate setting is `ddl-auto: validate`, so at startup it only checks that the entities match the tables, and it fails fast if you forgot to run `./gradlew flywayMigrate`.

The folder also contains a `.gitkeep` (an empty file that makes Git keep an empty folder). It is no longer needed now that the folder has migrations.
