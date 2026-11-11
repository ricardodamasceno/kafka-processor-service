# Redis

Redis is available in the local environment, the application has the client library and its connection settings, and the first use is the cache of the status of users ([ADR 0007](adr/0007-cache-the-status-of-users-in-redis.md)). **Nothing reads that cache yet**: it is written when a user is registered and whenever a status changes.

## Pieces

| Piece | Where | Role |
|---|---|---|
| `redis` service | `docker/docker-compose.yml` | Redis 7.4 (`redis:7.4-alpine`) on port 6379, with a health check (`redis-cli ping`) and the named volume `redis-data` for its snapshots |
| `redisinsight` service | `docker/docker-compose.yml` | Web interface for Redis (`redis/redisinsight:3.8.0`) on port 5540, with the named volume `redisinsight-data` for its own settings |
| `spring-boot-starter-data-redis` | `build.gradle` | Spring Data Redis and the Lettuce client |
| Connection settings | `src/main/resources/application.yml`, under `spring.data.redis` | Host, port and timeouts |

Spring Boot creates a `StringRedisTemplate` and a `RedisTemplate` from these settings, ready to inject.

## Start and use

Start only Redis:

```bash
docker compose -f docker/docker-compose.yml up -d redis
```

Check that it answers:

```bash
docker exec redis redis-cli ping
```

Open the command line:

```bash
docker exec -it redis redis-cli
```

Delete every key (local use only):

```bash
docker exec redis redis-cli flushall
```

## Web interface

RedisInsight is to Redis what Kafka UI is to Kafka: it lists and edits keys, runs commands and shows memory use. Open http://localhost:5540. It starts with the rest of the environment, or alone:

```bash
docker compose -f docker/docker-compose.yml up -d redisinsight
```

**First access.** RedisInsight shows its terms of use and privacy settings (sharing usage data, encrypting sensitive information, notifications). You have to accept the terms yourself and choose those settings there.

**The local Redis.** The service registers it at startup from the variables `RI_REDIS_HOST`, `RI_REDIS_PORT` and `RI_REDIS_ALIAS`, under the name `local`. RedisInsight does this only **after the terms are accepted**, so the database appears in the list once you have accepted them. If it does not appear, add it by hand with host `redis` and port `6379`. RedisInsight runs on the Compose network, so it reaches Redis by the service name. A desktop client on your machine uses `localhost:6379` instead.

Its own settings and connections are kept in the volume `redisinsight-data`. The interface has no login of its own, so it is for local use only.

## What the application stores

| Key | Value | Expires | Written when |
|---|---|---|---|
| `user:status:<userId>` | The status name: `ACTIVE`, `INACTIVE` or `BLOCKED` | After `app.cache.user-status-ttl` (1 minute) | A user is registered, and on every `PUT /users/{id}/status` (including one that sets the status the user already has, which also refreshes the expiry) |

The database is the source of truth, and the cache is a copy. Writing to Redis is best effort: if Redis is unavailable the application logs a warning and the operation still succeeds, so an entry can be missing or briefly different from the database. Whoever reads it must treat a missing entry as a miss and read the database. See [ADR 0007](adr/0007-cache-the-status-of-users-in-redis.md).

Look at the entries:

```bash
docker exec redis redis-cli --scan --pattern 'user:status:*'
```

## Application settings

| Property | Environment variable | Default | Notes |
|---|---|---|---|
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | |
| `spring.data.redis.connect-timeout` | | `2s` | How long to wait to open a connection |
| `spring.data.redis.timeout` | | `2s` | How long to wait for the answer to a command |
| `app.cache.user-status-ttl` | | `1m` | How long a cached user status lives. It also bounds how stale an entry can be after a lost write. Must be greater than zero |

The timeouts are short on purpose: when Redis is slow or gone, a request fails quickly instead of waiting on it.

There is no password locally. For another environment, set `SPRING_DATA_REDIS_PASSWORD` (Spring Boot's own environment variable binding for `spring.data.redis.password`).

## What was checked

With the application running against the local Redis:

- The application wrote a key and read it back.
- **With Redis stopped, the application still starts.** Connections are opened on first use, so a Redis outage does not stop the application. A call to Redis fails at once with `RedisConnectionFailureException`.
- The port from `REDIS_PORT` is applied, so the settings in `application.yml` are the ones in use.
- Against a server that accepts the connection and never answers, the call gave up after about 2 seconds.
- RedisInsight registered the database `local` (`redis:6379`) after the terms were accepted, connected to it, listed a key written with `redis-cli` and read its value.
- Registering a user wrote `user:status:<id>` with the value `ACTIVE` and an expiry of 60 seconds, the configured minute. The entry expired on its own after that, while the user stayed in the database. Each of the three statuses, set in any order, reached the database, Redis and the response together. Setting the same status again refreshed the expiry (from 15 back to 60 seconds). A rejected registration or an unknown user wrote nothing.
- **With Redis stopped**, registering a user answered 201 in 23 ms and updating a status answered 200, with the right value in the database and a warning in the log. After Redis came back, that user had no cached status until the next update wrote it again.

## Defaults on purpose

The Compose service has no password, no memory limit and no eviction policy, because the only use so far is a cache whose entries expire on their own, and other uses are not decided. As uses are added:

- **A cache** (data that can be recomputed) should set `maxmemory` and an eviction policy such as `allkeys-lru`, so Redis drops old entries when memory is full.
- **Data that must not disappear** (idempotency keys, state) must not be evicted. It also needs stronger persistence than the default snapshots, for example `appendonly yes`.

The data survives a restart of the container through the periodic snapshot Redis writes to `/data`. Removing the volume `redis-data` erases it.

## Known limitations

- The cache of user status decides what to do when Redis fails by being best effort: the operation succeeds and a warning is logged. Any other feature that uses Redis has to decide for itself: fail the request, or fall back to another source.
- One node, with no replication. There is no authentication and no TLS, so this is for local use only.
- The application does not check the Redis connection at startup and has no health endpoint for it.
