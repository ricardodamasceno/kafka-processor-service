# Redis

Redis is available in the local environment, and the application has the client library and its connection settings. **Nothing uses Redis yet.** What it will hold (a cache, idempotency keys, other state) is not decided, so there is no ADR yet. The first use should bring one.

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

## Application settings

| Property | Environment variable | Default | Notes |
|---|---|---|---|
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | |
| `spring.data.redis.connect-timeout` | | `2s` | How long to wait to open a connection |
| `spring.data.redis.timeout` | | `2s` | How long to wait for the answer to a command |

The timeouts are short on purpose: when Redis is slow or gone, a request fails quickly instead of waiting on it.

There is no password locally. For another environment, set `SPRING_DATA_REDIS_PASSWORD` (Spring Boot's own environment variable binding for `spring.data.redis.password`).

## What was checked

With the application running against the local Redis:

- The application wrote a key and read it back.
- **With Redis stopped, the application still starts.** Connections are opened on first use, so a Redis outage does not stop the application. A call to Redis fails at once with `RedisConnectionFailureException`.
- The port from `REDIS_PORT` is applied, so the settings in `application.yml` are the ones in use.
- Against a server that accepts the connection and never answers, the call gave up after about 2 seconds.
- RedisInsight registered the database `local` (`redis:6379`) after the terms were accepted, connected to it, listed a key written with `redis-cli` and read its value.

## Defaults on purpose

The Compose service has no password, no memory limit and no eviction policy, because what it will hold is not decided. When the first use is chosen:

- **A cache** (data that can be recomputed) should set `maxmemory` and an eviction policy such as `allkeys-lru`, so Redis drops old entries when memory is full.
- **Data that must not disappear** (idempotency keys, state) must not be evicted. It also needs stronger persistence than the default snapshots, for example `appendonly yes`.

The data survives a restart of the container through the periodic snapshot Redis writes to `/data`. Removing the volume `redis-data` erases it.

## Known limitations

- A feature that depends on Redis has to decide what to do when it fails: fail the request, or fall back to another source. Nothing decides this yet.
- One node, with no replication. There is no authentication and no TLS, so this is for local use only.
- The application does not check the Redis connection at startup and has no health endpoint for it.
