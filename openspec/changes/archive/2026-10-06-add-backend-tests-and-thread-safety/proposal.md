## Why

`LevelService` holds all game state in a plain `HashMap` and a mutable `Level`, but only some of its
methods are `synchronized`: the `@Scheduled` jobs (`cleanupInactiveBees`, `fillFlowers`,
`publishLevel`) and `restartLevel()` iterate and mutate the same state without the lock. With several
players clicking at once this can throw `ConcurrentModificationException` (a scheduled job iterating
`bees` while `registerBee` inserts) and loses updates on `Flower.fill` (harvest sets 0 while
`fillFlowers` writes the old value plus its rate). The only safety net today is
`GameResourceHarvestTest`; level, target, restart, inactive-bee cleanup and the SSE stream are
untested, so neither this fix nor the upcoming larger changes (leaderboard, clouds, server authority)
can be checked automatically.

## What Changes

- Make every state-touching path of `LevelService` run under one lock: `getLevel`, `restartLevel`,
  `cleanupInactiveBees`, `fillFlowers`, `publishLevel` join the already synchronized `registerBee`,
  `getLevelForPlayer`, `setTarget`, `harvest`.
- Hand out snapshots instead of live objects: the level returned to a player by
  `GET /api/level/{playerId}` is a copy (flowers and bees copied), so Jackson serialises it outside
  the lock without racing the scheduler.
- Publish SSE events outside the lock (serialise the level under the lock, emit afterwards).
- Move the initial `restartLevel()` from the constructor to `@PostConstruct`.
- Extend the backend test suite (`@QuarkusTest`, REST Assured, AssertJ) to cover `GET /api/level/{playerId}`,
  `POST /api/player/{id}/target`, `POST /api/admin/restart`, inactive-bee cleanup, the
  `level-update` event and a real HTTP subscription to `GET /api/events`, plus a concurrency
  stress test for `LevelService`.
- Run tests on a random HTTP port so they no longer clash with the frontend dev server on 8081.

No REST/SSE contract change: request and response shapes and event payloads stay exactly as they are
(including the still double-encoded `level-update`, which has its own backlog entry).

## Capabilities

### New Capabilities
- `game-session`: joining a game (`GET /api/level/{playerId}`), steering a bee
  (`POST /api/player/{id}/target`), restarting the level, removing inactive bees, broadcasting
  `level-update`/`levelRestarted` over SSE, and consistent game state under concurrent requests and
  scheduled jobs.

### Modified Capabilities
<!-- none: flower-harvest requirements stay unchanged; its existing tests keep covering them -->

## Non-goals

- Fixing the double-encoded `level-update` payload, the side effect of `GET /api/level` registering a
  bee, or `lastActive` only being refreshed by `setTarget` (separate backlog entries).
- Server-side authority (bee position check on harvest, protected admin restart, server-side honey).
- Running tests in CI (CI runs no tests; that stays as is).
- Frontend tests or frontend changes.

## Impact

- **Backend:** `services/LevelService.java` (locking, snapshots, `@PostConstruct`),
  `dtos/Flower.java` and `dtos/Bee.java` (`toBuilder = true` for copying),
  `src/test/resources/application.properties` (random test port), new test classes under
  `src/test/java/info/unterrainer/htl/`.
- **Frontend:** none.
- **Deploy/CI:** none.
- **API:** unchanged; `http/game.http` stays valid.
