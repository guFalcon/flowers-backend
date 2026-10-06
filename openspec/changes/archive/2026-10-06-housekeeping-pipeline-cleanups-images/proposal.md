## Why

Three small backlog items have been piling up: the backend pipeline still uses action majors that
run on the deprecated Node 20 runtime and installs Maven by hand with `sudo`; the backend has a few
leftovers (a duplicated colour palette, unused Dockerfiles, German comments, a rejoin that does not
count as activity); and the frontend image installs dev dependencies because of a typo, copies files
twice and runs on a different Node version than `.nvmrc`, which itself pins Node 20, end of life since
April 2026. None of these needs a design decision, so they ship together as one housekeeping change.

## What Changes

**Backend pipeline (`.github/workflows/pipeline.yml`)**
- Move to the Node 24 action majors used by the other pipelines: checkout v7, setup-java v6 (with
  its built-in Maven cache, replacing `actions/cache@v3`), upload/download-artifact v7/v8, docker
  qemu/buildx/login v4 and build-push v7.
- Drop the manual Maven 3.9.4 download/`sudo` install; use the committed Maven wrapper (`./mvnw`) for
  `versions:set` and `package`.
- Keep the own build and docker jobs; do not switch to the shared `docker-build-workflow` (see design).

**Backend cleanups**
- `GET /api/level/{playerId}` keeps joining (creating the bee if absent, as the `game-session` spec
  requires) and now also marks an existing bee as active, so a reload counts as activity like
  steering does.
- `LevelService.pickColor()` is removed; flower base colours come from `ColorUtils` (single palette,
  no duplicate `salmon`).
- Delete the unused `src/main/docker/Dockerfile.native`, `Dockerfile.native-micro` and
  `Dockerfile.legacy-jar`; fix `Dockerfile.jvm` to `EXPOSE 8084`, the port the app listens on.
- Translate the German comments in `GameResource` and `application.properties` to English.

**Frontend image and dependencies**
- `Dockerfile`: `npm ci --omit=dev`, one copy step per file kind instead of duplicate `COPY`s, base
  image `node:24-alpine`.
- `package.json`: drop the unused `express-session`, move `nodemon` to `devDependencies`, regenerate
  `package-lock.json`.
- `.nvmrc` from 20 to 24 (the shared `npm-build-workflow` reads it), README updated.
- `deploy/docker-compose.yml`: rename the service `test` to `frontend`. `EXPOSE 8080` stays: the
  compose file sets `INTERNAL_PORT=8080` and Traefik routes to 8080, so it is correct.

## Non-goals

- No heartbeat or keep-alive: a player who neither steers nor reloads still loses their bee after
  60 seconds.
- No switch from "GET joins" to an explicit join endpoint; no other REST/SSE contract change.
- No change to who may restart the level or harvest (backlog entry "Server trusts the client").
- No multi-arch images, no version tags on Docker Hub, no change to the deploy workflow.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `game-session`: joining with an existing `playerId` marks that bee as active (resets its
  inactivity timer).

## Impact

- **Backend:** `LevelService`, `GameResource`, `application.properties`, `src/main/docker/`
  (three files deleted, `Dockerfile.jvm` edited), new/extended tests in `GameResourceLevelTest` /
  `LevelServiceCleanupTest`.
- **Frontend:** `Dockerfile`, `package.json`, `package-lock.json`, `.nvmrc`, `README.md`. No change to
  the game code.
- **Deploy/CI:** backend `.github/workflows/pipeline.yml`; frontend `deploy/docker-compose.yml`
  (service rename; the container name `flowers-frontend` and the Traefik labels stay).
- **Docs/config:** `CLAUDE.md`, `openspec/config.yaml` and the memory files that say "Node 20".
- **Contract:** no request/response or SSE payload changes; `http/game.http` needs no update.
