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

## Current state

There are no migrations yet: the folder only contains a `.gitkeep` (an empty file that makes Git keep the folder). With an empty database, `flywayInfo` shows "No migrations found", which confirms the connection works.
