## 1. Backend

- [x] 1.1 Replace `INACTIVITY_TIMEOUT_MILLIS` in `LevelService` with config property `flowers.inactivity-timeout` (Duration, default `5m`, documented in `application.properties`); verify `LevelServiceCleanupTest` passes with timeouts adjusted to 5 minutes (idle > 5 min removed, idle 4 min kept)
- [x] 1.2 Add `retiredBees` map to `LevelService`: `removeInactiveBees` moves removed bees there frozen at their position at removal (target = position, one-keyframe path); verify with a cleanup test that the removed bee is gone from the level and a `level-update` is still published
- [x] 1.3 Restore retired bees in `addBeeIfAbsent` (same id, colour, honey, frozen position, `lastActive = now`, removed from `retiredBees`; name kept unless an active bee has it, then `BeeNames.pick`), reported as created so a `level-update` is published; verify with tests for "Retired bee rejoins", "Retired player sets a target" (path starts at retired position, honey kept), "Returning bee keeps its name", "Returning bee's name was taken" and "Returned bee times out again"
- [x] 1.4 Reset the honey of retired bees in `restartLevel`; verify with a test "Restart also resets retired bees"
- [x] 1.5 Update the "Rejoining keeps an idle bee alive" test to 4 min idle + cleanup 2 min later; verify the full suite `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw test` is green (incl. `LevelServiceConcurrencyTest`)
- [x] 1.6 Update `http/game.http` header comment (5-minute inactivity timeout, retired bees come back with name/colour/honey on join or steer); verify `game.http` still runs green against `quarkus:dev`
- [x] 1.7 Align the stale cloud checks with the clouds spec (12 to 18 clouds, size 0.06 to 0.16): the assertion in `http/game.http` and the restart requirement in the game-session delta spec; verify `game.http` runs green against `quarkus:dev`

## 2. Frontend

- [x] 2.1 In `harvest.js`, when the own bee instance is missing (or `state.yourBeeId` is null) on `pointerdown`, `await init()` from `level.js` (guarded by a shared in-flight promise against parallel rejoins), then continue with the normal setTarget flow for the clicked coordinates; verify by code review that a click with the bee on screen sends setTarget without a level fetch
- [x] 2.2 In the setTarget response handler, look up the own bee instance anew (`beeInstances.get(state.yourBeeId)`) and run the flight-id check against it; verify no console error after a rejoin click

## 3. Verification

- [x] 3.1 Headless (Playwright) against local `quarkus:dev -Dflowers.inactivity-timeout=15s` and the local frontend: load the page, steer once, harvest if possible, wait until the own bee disappears, click a point → own bee reappears with the same name and honey in the leaderboard and flies to the clicked point, without reload; a second normal click works as before
- [x] 3.2 Stop every server started for the checks (8084, 8081) and verify with `ss -ltn`
