## 1. Backend

- [x] 1.1 In `LevelService`, factor the deep-copy logic of `snapshotFor()` into a private helper and make `buildLevelUpdate()` put that copied `Level` object (not a JSON string) into the `level-update` map; remove the `ObjectMapper` injection if unused; verify `./mvnw test` (JAVA_HOME=/usr/lib/jvm/java-21-openjdk) still passes
- [x] 1.2 Add a `@QuarkusTest` (e.g. in `EventStreamTest`) that subscribes to `/api/events`, sets a bee target and asserts the `level-update` data line contains `"level":{` with the bee and the flowers; verify it passes and fails against the old string payload
- [x] 1.3 Update `http/game.http` comments/requests describing the `level-update` event to the object form; run the file against `quarkus:dev` on :8084 and verify all checks pass

## 2. Frontend

- [x] 2.1 In `events.js`, drop the `JSON.parse` fallback and pass `data.level` straight to `applyLevel()`; verify via headless browser that level-updates still render
- [x] 2.2 In `flowers.js`, store a signature (`x, y, size, petals, color, petalColors, stampColor` plus the play-area width/height, so a resize still relayouts flowers with the next update) on each flower element in `createFlower()` and rewrite `buildLevel()` to reconcile by id (update fill/rate on match, replace on signature mismatch, add new, remove missing); verify with Playwright that flower elements keep identity across two level-updates
- [x] 2.3 Change `startFillGrowth()` to a 1000 ms interval; verify in the browser that fill grows between updates and snaps to the server value on each level-update

## 3. Verification

- [x] 3.1 Run backend and frontend locally (8084/8081), drive the game headless with Playwright: flower nodes stay the same across periodic updates, harvest animation and fill still work, admin restart redraws flowers with new positions/colours, flower count changes are reflected; stop both servers afterwards and verify ports are free
- [x] 3.2 Run `openspec validate sync-level-state --strict` and verify it reports the change as valid
