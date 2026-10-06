## 1. Backend — test setup and baseline

- [x] 1.1 Add `quarkus.http.test-port=0` to `src/test/resources/application.properties`; verify `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw test` still passes `GameResourceHarvestTest` while a process listens on 8081 (or confirm in the log that a random port is used)
- [x] 1.2 Write `LevelServiceConcurrencyTest` (16 threads, a few thousand mixed join/steer/harvest/getLevelForPlayer/restart calls while another thread loops `fillFlowers`/`cleanupInactiveBees`/`publishLevel`; asserts no exception and all joined bees present) and run it against the **unchanged** `LevelService`; record in this task whether it fails (expected: `ConcurrentModificationException` or a missing bee)
  - Result 2026-10-06 against the unchanged service: failed 5 of 5 runs, each with `ConcurrentModificationException` in `cleanupInactiveBees` (`HashMap` iteration while `registerBee` inserts). A first version of the test also reported missing bees, but that was a test bug (with 200 players and 5 operation types every player always got the same operation, so some never joined); fixed by joining every player in round 0.

## 2. Backend — thread-safety of LevelService

- [x] 2.1 Add `toBuilder = true` to `@Builder` on `Flower` and `Bee`; verify `./mvnw compile` succeeds
- [x] 2.2 Move the initial `restartLevel()` from the constructor to a `@PostConstruct` method; verify `GameResourceHarvestTest` passes
- [x] 2.3 Make `getLevel`, `restartLevel`, `fillFlowers`, `harvest` synchronized; split `registerBee`, `setTarget`, `cleanupInactiveBees` into a synchronized state part and a public wrapper that publishes afterwards; add synchronized `buildLevelUpdate()` and make `publishLevel()` emit outside the lock (design D1, D2); verify `LevelServiceConcurrencyTest` now passes 5 runs in a row (`-Dtest=LevelServiceConcurrencyTest`, repeated)
  - Result 2026-10-06: 5 of 5 runs green, ~0.8 s each
- [x] 2.4 Make `getLevelForPlayer()` return a deep copy (new flower and bee objects) and add a Javadoc to `getLevel()` marking it as live state; verify by a test that mutating a flower of the returned level does not change the service's level
- [x] 2.5 Extract `cleanupInactiveBees(long now)` (package-private) and call it from the scheduled no-arg method with the current time (design D5); verify `./mvnw compile` succeeds

## 3. Backend — tests for game-session

- [x] 3.1 `GameResourceLevelTest`: join (200, `yourBeeId`, own bee in `bees`, 6–11 flowers) and rejoin (still exactly one bee, same colour); verify the tests pass
- [x] 3.2 `GameResourceLevelTest`: target for a known player (200 `{"status":"ok"}`, target in next level, `level-update` event whose parsed `level` contains the new target), for an unknown player (bee created with that target) and with a missing `y` (treated as 0); verify the tests pass
- [x] 3.3 `GameResourceLevelTest`: restart (200 with `status`/`message`, exactly one `levelRestarted` event, 6–11 flowers, previously joined bee still present); verify the test passes
- [x] 3.4 `LevelServiceCleanupTest` (package `info.unterrainer.htl.services`): `p1` removed, `p2` kept, one `level-update` published, using `cleanupInactiveBees(t0 + 60_001)`; verify the test passes
- [x] 3.5 `EventStreamTest`: open `GET /api/events` via `java.net.http.HttpClient` (`@TestHTTPResource`), harvest a filled flower, assert a `data:` line with `"type":"harvest"` and the flower id arrives within 5 s, close the connection in `finally`; verify the test passes and does not hang
- [x] 3.6 Harvest/growth interleaving: a test that runs `harvest` and `fillFlowers` concurrently many times on one flower and asserts the fill afterwards is 0 or exactly its rate; verify the test passes
  - Result 2026-10-06: green; with `synchronized` removed from `fillFlowers` it fails 3 of 3 runs with the lost update (fill = 0.5 + rate)

## 4. Verification

- [x] 4.1 Full suite: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw test` is green
- [x] 4.2 Start `./mvnw quarkus:dev` on 8084 and run `http/game.http` with the IntelliJ HTTP client container (see `ai/memory/reference_build_and_test.md`); all requests pass unchanged (no contract change); stop the dev server and verify port 8084 is free
- [x] 4.3 Update `ai/memory/reference_build_and_test.md` (random test port, new test classes); verify by reading the file
