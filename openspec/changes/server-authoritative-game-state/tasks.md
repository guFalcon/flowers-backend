## 1. Backend

- [x] 1.1 Extend `Bee` with `honey` (`long`) and the `@JsonIgnore` flight fields `fromX`, `fromY`, `flightStart`, `flightEnd`, plus `positionAt(now)` (linear interpolation, target after arrival); verify with a plain unit test for start, half-way and after-arrival positions
- [x] 1.2 In `LevelService`, add `now`-taking package-private overloads (D2); register new bees standing still (target = position, `flightEnd` = now, honey 0); `setTarget` freezes the current position as flight start and sets `flightEnd = now + max(5000 × d, 200)`; snapshots write `positionAt(now)` into `x`/`y`; verify with tests for the "New bee stands still", "Flight duration", "Redirect mid-flight" and "Level shows the bee in flight" scenarios
- [x] 1.3 Replace `harvest(flowerId)` by `harvest(playerId, now)` per D3 (arrival with 500 ms tolerance, closest flower within `0.175 × size` with the 9:16 conversion, fill > 0.1, yield `round(fill² × 1000)` added to honey, mark bee active) returning a result record (`flowerId`, `gained`, `total`, or "unknown bee"); verify with service tests for every `flower-harvest` scenario (arrived, in flight, 200 ms early, off-flower, almost empty, closest of two flowers)
- [x] 1.4 Make `restartLevel()` reset every bee's honey to 0 while keeping positions and flights; verify with a test for "Restart starts a new round"
- [x] 1.5 In `GameResource`, remove `POST /api/harvest/{flowerId}`, add `POST /api/player/{playerId}/harvest` (404 for unknown bee, body `{flowerId, gained, total}`, `harvest` SSE event only when `gained > 0`); rewrite `GameResourceHarvestTest` and adapt `EventStreamTest`/`LevelServiceConcurrencyTest` to the new endpoint (positioning bees via the `now` overloads); verify `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw test` passes, including a test that the old endpoint answers 404
- [x] 1.6 Add `AdminTokenFilter` (D4) for `/api/admin/*`, config `flowers.admin-token=${FLOWERS_ADMIN_TOKEN:}`, a `%dev` and `%test` token, and `x-admin-token` in the CORS headers; verify with `@QuarkusTest`s for correct token, missing token, wrong token (`true`), and the CORS preflight; verify blank-token behaviour with a test profile or a unit test of the filter
- [x] 1.7 Update `http/game.http`: new harvest endpoint (in-flight → `gained` 0, unknown player → 404, old endpoint → 404), restart without token → 403 and with the dev token → 200, comments on the new shapes; run it against `quarkus:dev` on :8084 and verify all checks pass

## 2. Frontend

- [x] 2.1 `config.js`: `HARVEST_URL` takes the player id (`/api/player/{id}/harvest`); `harvest.js`: drop the DOM closest-flower search and `state.userHoney`, POST the harvest after the guarded flight timer, slurp + show `total` if `gained > 0`, else bump; remove `userHoney` from `state.js`; verify headless that clicking a filled flower increases the honey display and clicking empty grass plays the bump path (no honey change)
- [x] 2.2 Show the own bee's `honey` after `init()` and `applyLevel()`; verify headless that the honey survives a page reload and shows 0 after an admin restart
- [x] 2.3 `bee.js`: add `placeAt(x, y)`; `bees.js`: place a newly created bee at its `x`/`y`, then fly to its target only if it differs; verify headless that a freshly joined bee's element is at its server position immediately after load (no transition from the centre) and that a reload mid-flight resumes from the current position
- [x] 2.4 `admin.js`: token from `?admin=`, panel only for a non-empty value, `X-Admin-Token` header on restart, "Admin token rejected" on 403 without reloading the level; verify headless with the right token and with `?admin=true`

## 3. Deploy/CI

- [x] 3.1 Generate a random admin token into `ai/secrets/flowers-admin-token` (git-ignored) and verify `git status` does not show it
- [x] 3.2 Extend `UnterrainerInformatik/deploy-workflow` with the optional secret `EXTRA_ENV`, appended to `deploy/.env` only when non-empty (D5); verify the YAML parses (`actionlint` or `python -c "import yaml"`) and that existing callers need no change
- [x] 3.3 Backend `pipeline.yml` passes `EXTRA_ENV: FLOWERS_ADMIN_TOKEN=${{ secrets.FLOWERS_ADMIN_TOKEN }}`; `deploy/docker-compose.yml` sets `FLOWERS_ADMIN_TOKEN=${FLOWERS_ADMIN_TOKEN}`; verify `docker compose -f deploy/docker-compose.yml config` with a dummy `.env` shows the variable
- [x] 3.4 After confirming with Gerald, set the repo secret `FLOWERS_ADMIN_TOKEN` on `guFalcon/flowers-backend` via `gh secret set` from the file in `ai/secrets/`; verify with `gh secret list`

## 4. Docs and backlog

- [x] 4.1 Update both READMEs (new harvest endpoint, admin token / `?admin=<token>`, `FLOWERS_ADMIN_TOKEN`) and the endpoint list in `ai/memory/project_flowers.md`; verify no reference to `/api/harvest/{flowerId}` or `?admin=true` remains (`grep -r` in both repos, excluding `openspec/changes/archive`)
- [x] 4.2 Delete the "Server trusts the client" entry from `ai/open-proposals.md` and adjust the leaderboard entry (server-side honey now exists); verify by reading the file

## 5. Verification

- [x] 5.1 Run backend (quarkus:dev, dev token) and frontend locally, drive two players headless with Playwright: spawn without flight, harvest only on arrival, honey per player in the level, reload keeps honey, admin restart with/without token, bump on empty grass; stop both servers and verify ports 8084/8081 are free
- [ ] 5.2 After deployment: restart on the live backend without token → 403, with `?admin=<token>` → works; verify with `curl` and one headless run against `https://flowers.htl.dev`
- [x] 5.3 Run `openspec validate server-authoritative-game-state --strict` and verify it reports the change as valid
