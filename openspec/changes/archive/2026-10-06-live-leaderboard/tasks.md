## 1. Backend

- [x] 1.1 Add `BeeNames` with the 48-name list from design.md and `pick(Set<String> usedNames, Random random)` (unused list name, else list name + smallest free suffix from 2); verify with a unit test `BeeNamesTest` (≥ 40 distinct names, picks an unused name, suffix when all are used, suffix skips taken numbers)
- [x] 1.2 Add `name` to `Bee`; assign it in `LevelService.addBeeIfAbsent` from the names of the current bees under the lock; verify with `GameResourceLevelTest`: new bee has a name from the list, rejoin and admin restart keep the name, 10 joins yield 10 distinct names
- [x] 1.3 Extend the harvest SSE event in `GameResource.harvest` with `beeId` and `honey` (= `total`); verify with `EventStreamTest`: subscriber receives `beeId` and `honey` equal to the harvest response's `total`
- [x] 1.4 Verify the name round-trips through JSON with umlauts (a test that a bee's `name` in the level response equals the stored name, covering e.g. `Gänseblümchen` via `BeeNames` JSON-serialisation or a direct `Bee` mapping test)
- [x] 1.5 Run the full backend suite `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw test`; all tests green

## 2. Frontend

- [x] 2.1 Add `leaderboard.js` with `renderLeaderboard(bees)` (honey desc, ties by name with `localeCompare(…, "de")`, top 5 + own row with real rank, own row highlighted, `textContent` only) and `applyHarvestToLeaderboard(event)` (patches `honey` of `beeId` in `state.levelData.bees`, re-renders); verify in the headless check (3.2)
- [x] 2.2 Add the panel `<div class="leaderboard" id="leaderboard">` inside `#playArea` and `<span id="ownName">` in the `.stats` bar in `index.html`; style in `styles.css` (top-left, translucent, `clamp()` font, `pointer-events: none`, `.is-self` highlight); verify clicks through the panel still steer the bee (3.2)
- [x] 2.3 Wire `level.js` (`init`, `applyLevel`: render leaderboard, show own name) and `events.js` (`harvest`: `applyHarvestToLeaderboard`); verify in the headless check (3.2)

## 3. http files and verification

- [x] 3.1 Update `http/game.http`: header comment documents `name` in every bee and the harvest event `{"type","flowerId","fill","beeId","honey"}`; the level request asserts the own bee has a non-empty `name`; run it against `quarkus:dev` on :8084, all assertions pass
- [x] 3.2 Headless browser check (Playwright, frontend on :8081 rerouted to local backend on :8084) with two players: both see a panel with both names sorted by honey, own row highlighted, own name next to the honey; after a harvest by player A, player B's panel updates before the next `level-update`; clicking the meadow under the panel moves the bee; after an admin restart all rows show 0
- [x] 3.3 Stop every server started for the checks (8084, 8081) and verify with `ss -ltn`
