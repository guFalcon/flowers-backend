# Open proposals

Drafted-but-not-yet-proposed work for backend and frontend. Delete an entry as soon as it becomes
an `/opsx:propose` change — never tick it off.

## Backend pipeline on old action majors
Org scan of 2026-10-02 (done from presserl) found `flowers-backend`'s `.github/workflows/pipeline.yml`
still on cache v3, setup-java v4, up/download-artifact v4 and docker actions v3/v5 (Node 20). Move
it to the Node 24 majors the other pipelines use (checkout v7, cache v6, setup-java v6,
upload/download-artifact v7/v8, docker qemu/buildx/login v4 + build-push v7), or replace the
hand-rolled build/docker jobs with the shared `docker-build-workflow` like the frontend does. The
manual Maven 3.9.4 install step becomes unnecessary with the Maven wrapper. See
`ai/memory/reference_ci_runners.md`.

## Backend tests
There is no `src/test` yet. A first `@QuarkusTest` suite for `GameResource` (level, target,
harvest, restart, SSE events) would give later changes a safety net.

## Thread-safety of LevelService
`LevelService.bees` is a plain `HashMap`; the `@Scheduled` methods `cleanupInactiveBees()`,
`publishLevel()` and `fillFlowers()` are not `synchronized`, while `setTarget`/`registerBee`/`harvest`
are. Concurrent clicks can raise `ConcurrentModificationException` and race on `Flower.fill`. Use a
`ConcurrentHashMap` and/or synchronize all mutating and iterating paths; `restartLevel()` too.

## Harvest SSE event fires on the wrong condition (bug, confirmed by Gerald 2026-10-06)
`GameResource.harvest` publishes `{"type":"harvest","flowerId"}` only when `honey == 0`, i.e. on a
failed harvest. It should fire on a successful harvest so other clients see the emptied flower
immediately instead of after the next `level-update` (≤3 s). Contract change → frontend + `.http`.

## level-update payload is double-encoded
`publishLevel()` serialises the level to a JSON string and nests it in the event (`level` is a
string); the frontend re-parses it. Send the `Level` object directly. Contract change → both repos.

## Server trusts the client
Any client can harvest any flower from anywhere (no bee-position check), `POST /api/admin/restart` is
unprotected (frontend admin mode is just `?admin=true`), and the honey score lives only in the
browser (lost on reload). Decide what should be authoritative on the server — ties into the
leaderboard entry.

## Backend small cleanups
`GET /api/level/{playerId}` registers a bee as a side effect; `lastActive` is only refreshed by
`setTarget`. `LevelService.pickColor()` duplicates the `ColorUtils` palette and lists `salmon` twice.
Unused `Dockerfile.native*`/`legacy-jar`. German comments in `GameResource`/`application.properties`.

## Frontend rebuilds all flowers on every level-update
`buildLevel()` removes and recreates every flower DOM node on each `level-update` (every 3 s plus
every player click). Update flowers in place (by id) instead.

## Flower fill computed twice
Client grows `fill` by `rate` every 2 s, server every 1 s — the two drift until the next
`level-update`. Either take fill only from the server or use the same rate/interval.

## QR modal close leaks listeners
`closeQrModal()` in `index.html` calls `addEventListener` instead of `removeEventListener`, so click
listeners accumulate with every open/close.

## Frontend Dockerfile and dependencies
`npm install -only=production` is a typo (installs everything; use `npm ci --omit=dev`); files are
copied twice (`COPY *.js`, `*.css`); `nodemon` and unused `express-session` are runtime dependencies;
image uses Node 22 while `.nvmrc` says 20; `EXPOSE 8080`; compose service is named `test`.

## Restructure the frontend code
Requested by Gerald 2026-10-06 for readability: `index.html` holds ~400 lines of inline script
(config, layout, flower rendering, bee rendering, SSE handling, input, harvest, admin/QR). Split into
modules (e.g. `config.js`, `flowers.js`, `game.js`, `admin.js`), keep no build step. Natural first
step before the leaderboard and clouds.

## Live leaderboard for all players
Requested by Gerald 2026-10-06: show every player a current leaderboard. Needs server-side honey per
bee (see "Server trusts the client"), a leaderboard in the level/SSE payload or its own event, and
a frontend panel. Open: player names/colours, reset on level restart. Contract change → both repos.

## Clouds that slow bees down
Requested by Gerald 2026-10-06: clouds on the meadow that slow bees flying through them, so players
learn to route around them. Open: static vs drifting clouds, server- or client-side flight time,
how harvest timing accounts for the slowdown. Probably after the frontend restructure.

## mvnw is not executable in git
Found during `add-readmes` apply (2026-10-06): `mvnw` is tracked as `100644`, so `./mvnw` fails with
"permission denied" on a fresh clone; `sh mvnw` works. Fix with
`git update-index --chmod=+x mvnw` and drop the workaround note from the README.
