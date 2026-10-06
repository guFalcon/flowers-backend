## Context

See proposal.md (Why). `LevelService` is an `@ApplicationScoped` bean that owns `currentLevel` (with a
mutable `List<Flower>`) and `Map<String, Bee> bees`. Four methods are `synchronized`; the three
`@Scheduled` jobs and `restartLevel()`/`getLevel()` are not. `getLevelForPlayer()` returns
`currentLevel.toBuilder()...build()`, a shallow copy that still shares the live `Flower` and `Bee`
objects, which Jackson then serialises outside any lock. `GameResource` publishes the `harvest` and
`levelRestarted` events itself after calling the service. Tests already run with
`quarkus.scheduler.enabled=false`, so scheduled methods can be called directly and deterministically.

## Goals / Non-Goals

**Goals:**
- One simple, explainable locking model that students can follow.
- Tests that exercise the real HTTP layer, including one real SSE connection.

**Non-Goals:**
- Fine-grained or lock-free concurrency (the state is tiny; contention is irrelevant).
- Changing any REST/SSE payload.

## Decisions

### D1: One monitor (`synchronized` on the service) for all state access
All methods that read or write `currentLevel`, its flowers or `bees` become `synchronized`:
`getLevel`, `restartLevel`, `registerBee`, `getLevelForPlayer`, `setTarget`, `harvest`,
`cleanupInactiveBees`, `fillFlowers` and the state part of `publishLevel`. `bees` stays a `HashMap`,
guarded by the monitor.

*Alternative:* `ConcurrentHashMap` only. Rejected: it removes the `ConcurrentModificationException`
but not the lost update on `Flower.fill` (read-modify-write across two methods) and not the
check-then-act in `registerBee`. A single lock fixes all three and is easier to teach.
*Alternative:* `ReentrantReadWriteLock`. Rejected as over-engineering for a handful of players.

The lock is reentrant, so the existing internal calls (`setTarget` → `registerBee` → `publishLevel`)
keep working. ArC client proxies delegate to the real instance, so the monitor is the single bean
instance.

### D2: Snapshots out, events published outside the lock
- `getLevelForPlayer()` (the only level handed to clients) returns a deep copy: a new `Level` with
  copied `Flower`s and `Bee`s (`toBuilder = true` on both DTOs; `petalColors` is never mutated after
  creation and may be shared). Jackson serialises the snapshot without holding the lock and without
  seeing concurrent writes.
- `getLevel()` keeps returning the live level (now synchronized). Its only caller is the test suite,
  which deliberately mutates flowers through it (e.g. `setFill(0.5)` in `GameResourceHarvestTest`);
  a Javadoc marks it as live state for tests and internal use.
- SSE events are emitted after the lock is released. A private synchronized
  `buildLevelUpdate()` refreshes `currentLevel.bees` and serialises the `level-update` message; the
  public `publishLevel()` (not synchronized) calls it and then `eventBusService.publish(...)`.
  `registerBee`, `setTarget` and `cleanupInactiveBees` are split the same way: a synchronized private
  part changes the state, and the public method calls `publishLevel()` once that part has returned.
  This way a slow SSE subscriber can never block game logic.
- Event payloads stay byte-for-byte compatible (`level` is still a JSON string).

*Alternative:* serialise directly under the lock and keep publishing inside it. Simpler, but couples
SSE back-pressure to the game lock; the split costs only a few lines.

### D3: Initial level in `@PostConstruct`
`restartLevel()` moves from the constructor to a `@PostConstruct` method. CDI client proxies are
subclasses whose construction would otherwise also run game logic, and it makes initialisation
happen after injection, so `restartLevel()` may later safely use injected beans.

### D4: Test layout
- Keep `GameResourceHarvestTest` unchanged (it covers `flower-harvest`).
- `GameResourceLevelTest`: join, rejoin, target for known/unknown player and with a missing
  coordinate, restart (response, `levelRestarted` event, fresh flowers, bees kept).
- `LevelServiceCleanupTest`: inactive-bee removal (see D5).
- `EventStreamTest`: a real HTTP SSE subscription using `java.net.http.HttpClient` with
  `BodyHandlers.ofLines()` against `@TestHTTPResource("/api/events")`; trigger a successful harvest
  and read until a `data:` line with `"type":"harvest"` arrives, with a hard timeout.
- `LevelServiceConcurrencyTest`: an `ExecutorService` with 16 threads runs a few thousand mixed
  `registerBee`/`setTarget`/`harvest`/`getLevelForPlayer`/`restartLevel` calls while another thread
  loops `fillFlowers`/`cleanupInactiveBees`/`publishLevel`; assert all futures complete without
  exception and every joined bee is present afterwards.
- `level-update` events are captured by subscribing to `EventBusService.eventStream()` as the harvest
  test does; the `level` string is parsed with the injected `ObjectMapper`.
- Bees survive between tests (the service is application-scoped and has no reset). Tests use unique
  player ids (`UUID`) and assert only on their own bees, so no test-only reset method is needed.
- `quarkus.http.test-port=0` in `src/test/resources/application.properties` (random port; REST
  Assured and `@TestHTTPResource` pick it up automatically).

### D5: Testing the 60-second timeout
`cleanupInactiveBees()` reads `System.currentTimeMillis()`. To test it without waiting a minute, the
logic moves into a package-private overload `cleanupInactiveBees(long now)`; the scheduled no-arg
method calls it with the current time. The test (in package `info.unterrainer.htl.services`)
registers `p1`, waits a few milliseconds, records `t0`, registers `p2`, and calls
`cleanupInactiveBees(t0 + 60_001)`: `p1` (last active before `t0`) is removed, `p2` (last active
after `t0`) is kept, and one `level-update` is published.

*Alternative:* inject a `java.time.Clock`. Cleaner, but touches more code than this change needs.

## Risks / Trade-offs

- [Concurrency test is probabilistic: it can pass even if a race remains] → It reliably failed
  against the old code in practice for CME (many iterations, many threads); verify once by running it
  against the unmodified `LevelService` before applying D1 and note the result in the task.
- [SSE test may hang if no event arrives] → Read on a separate thread / with a hard timeout
  (`orTimeout`) and close the connection in a `finally`.
- [Deep copies on every `GET /api/level`] → A dozen small objects per request; negligible.
- [Ordering: an event published outside the lock may overtake one from another thread] → Acceptable;
  every `level-update` carries the full level, so the last one received wins, and the periodic
  broadcast corrects any staleness within 3 seconds.

## Migration Plan

Backend-only, no contract change. Deploy by pushing to `main`; rollback by reverting the commit.
