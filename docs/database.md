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

## Current migrations

| Version | File | Creates |
|---|---|---|
| V1 | `V1__add_click.sql` | Table `clicks` (id, ad_id, user_id, clicked_at) and an index on `(ad_id, clicked_at)`. Removed again by V4 |
| V2 | `V2__add_transaction.sql` | Table `transactions` (id, account_id, type, amount, occurred_at), checks on `type` and `amount > 0`, and an index on `(account_id, occurred_at)` |
| V4 | `V4__drop_click.sql` | Drops the `clicks` table (clicks are no longer part of the service). V1 stays in the history because an applied migration is never edited |
| V5 | `V5__enable_cdc_for_transactions.sql` | Sets `REPLICA IDENTITY FULL` on `transactions`, so the change events published by Debezium carry the complete old row. See [debezium.md](debezium.md) |
| V6 | `V6__add_transaction_status.sql` | Adds `status` to `transactions` (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`). Existing rows become `COMPLETED`, and the default is dropped afterwards so every insert states its status |

Timestamps use `TIMESTAMPTZ` and money uses `NUMERIC(19, 2)`.

The Spring Boot application never runs these migrations. Its Hibernate setting is `ddl-auto: validate`, so at startup it only checks that the entities match the tables, and it fails fast if you forgot to run `./gradlew flywayMigrate`.

The folder also contains a `.gitkeep` (an empty file that makes Git keep an empty folder). It is no longer needed now that the folder has migrations.
