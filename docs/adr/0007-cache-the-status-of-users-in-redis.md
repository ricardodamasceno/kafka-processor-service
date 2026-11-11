# 0007. Cache the status of users in Redis

- Status: Accepted
- Date: 2026-11-11

## Context

Creating a transaction checks that its user exists and is `ACTIVE`, which today means reading the user from PostgreSQL on every request. The status is small, read often and changes rarely, so it is a good first use for Redis. This is the first use of Redis in the project.

## Decision

- **The status is written to Redis every time it is saved:** when a user is registered and whenever `PUT /users/{id}/status` changes it. The write happens **after** the database write.
- **One entry per user:** the key is `user:status:<userId>`, the value is the status name (`ACTIVE`, `INACTIVE` or `BLOCKED`), and the entry expires after `app.cache.user-status-ttl` (one minute by default). A write refreshes the expiry.
- **The database is the source of truth.** The cache is a copy.
- **Writing the cache is best effort.** If Redis is unavailable, the failure is logged as a warning and the operation succeeds, because the database already holds the right value. The port `UserStatusCache` documents that an implementation must not throw.
- **`PUT /users/{id}/status` accepts any of the three statuses from any other.** There are no forbidden transitions. Setting the status a user already has is accepted, and it refreshes the cache. The answer is 200 with the user, 404 for an unknown user, and 400 for a missing or unknown status (the values are case sensitive).
- **Nothing reads the cache yet.** Creating a transaction still reads the status from the database. Using the cache there is a separate step.

Alternatives considered:

- **Fill the cache only when a user is read (read-through).** It needs no write on registration, but the first read after every change would still go to the database, and the status of a new user would not be cached until then.
- **No expiry.** A status written to the database but lost on its way to Redis would stay wrong until the next update. The expiry limits how long that can last.
- **Fail the request when the cache cannot be written.** It would make registering and blocking users depend on Redis being up, for data that Redis only copies.
- **Delete the entry on update and let the next read refill it** (invalidation). It avoids writing a value that may already be stale, but it needs the read side to exist first.

## Consequences

- The cached status can be **missing** (Redis was down when it changed, or the entry expired) or briefly **different from the database**. Two concurrent updates can reach Redis in a different order than they reached the database. The expiry and the next update correct it. A reader must treat a missing entry as a miss and fall back to the database, and should not trust the cache for anything that must never be wrong.
- The expiry is short (one minute). A lost write is corrected quickly, but an entry also disappears one minute after the last write. Since nothing but a write fills the cache today, a reader would find most entries missing. When a reader is added, it must fill the cache itself on a miss (cache-aside), and the expiry should be reviewed.
- The database write and the cache write are not atomic. This is accepted because of the best-effort rule above.
- A Redis outage does not stop users from being registered or updated. The cost is one failed call to Redis for each write, which is fast when the connection is refused and gives up after the 2 second timeout when Redis hangs.
- Blocking a user stops the creation of new transactions, but does not affect those that are already `PENDING`.
- The endpoint has no authentication and records neither who changed a status nor when. Anyone who can reach the API can block a user.
- The Redis service keeps its defaults (no password, no memory limit, no eviction policy), which are enough for entries that expire on their own. See [redis.md](../redis.md).
