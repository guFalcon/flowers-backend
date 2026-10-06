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

## Server trusts the client
Any client can harvest any flower from anywhere (no bee-position check), `POST /api/admin/restart` is
unprotected (frontend admin mode is just `?admin=true`), and the honey score lives only in the
browser (lost on reload). Decide what should be authoritative on the server — ties into the
leaderboard entry.

## Backend small cleanups
`GET /api/level/{playerId}` registers a bee as a side effect; `lastActive` is only refreshed by
`setTarget`. `LevelService.pickColor()` duplicates the `ColorUtils` palette and lists `salmon` twice.
Unused `Dockerfile.native*`/`legacy-jar`. German comments in `GameResource`/`application.properties`.

## Frontend Dockerfile and dependencies
`npm install -only=production` is a typo (installs everything; use `npm ci --omit=dev`); files are
copied twice (`COPY *.js`, `*.css`); `nodemon` and unused `express-session` are runtime dependencies;
image uses Node 22 while `.nvmrc` says 20; `EXPOSE 8080`; compose service is named `test`.

## Live leaderboard for all players
Requested by Gerald 2026-10-06: show every player a current leaderboard. Needs server-side honey per
bee (see "Server trusts the client"), a leaderboard in the level/SSE payload or its own event, and
a frontend panel. Open: player names/colours, reset on level restart. Contract change → both repos.

## Clouds that slow bees down
Requested by Gerald 2026-10-06: clouds on the meadow that slow bees flying through them, so players
learn to route around them. Open: static vs drifting clouds, server- or client-side flight time,
how harvest timing accounts for the slowdown. Probably after the frontend restructure.
