## Context

See proposal.md for the motivation. Relevant current state:

- The backend pipeline has its own `build` job (self-hosted runner, `setup-java`, Maven installed by
  hand with `sudo`, artifact `app-target` = the whole `target/` directory) and its own `docker-build`
  job that downloads `target/` and builds `src/main/docker/Dockerfile.jvm` for `linux/amd64`, tagged
  `:latest` only.
- The shared `docker-build-workflow` (used by the frontend) downloads an artifact named
  `build-artifacts` into `dist/`, builds `linux/amd64` and `linux/arm64/v8` and adds version tags.
- `LevelService.addBeeIfAbsent` sets `lastActive` only when it creates a bee; `updateTarget` sets it
  on every steer. The frontend calls `GET /api/level/{playerId}` once per page load.
- The frontend `Dockerfile` copies 14 files by name and then `*.js`/`*.css` again; it does not copy
  the newer ES modules by name, so the `*.js` glob is what makes the image work today.

## Goals / Non-Goals

**Goals:** no Node 20 actions or runtimes left in either pipeline or image; smallest possible diff
to the parts that deploy (pipeline, Dockerfiles, compose); one colour palette.

**Non-Goals:** reworking the backend build into the shared workflows; changing image tags or
platforms.

## Decisions

### Keep the backend's own build and docker jobs, only upgrade them
Alternative: switch the docker job to the shared `docker-build-workflow`. Rejected for now because
it expects its input in `dist/` under the artifact name `build-artifacts`, so `Dockerfile.jvm` would
have to `COPY dist/...` (breaking the local `./mvnw package && docker build` recipe, or needing a
build arg the shared workflow cannot pass), and it would add an arm64 build under QEMU nobody needs.
Upgrading the action majors gets rid of the Node 20 warnings with no change to the image.

Concretely: `actions/checkout@v7`, `actions/setup-java@v6` with `cache: maven` (replaces
`actions/cache@v3`), `actions/upload-artifact@v7`, `actions/download-artifact@v8`,
`docker/setup-qemu-action@v4`, `docker/setup-buildx-action@v4`, `docker/login-action@v4`,
`docker/build-push-action@v7`. Upload only `target/quarkus-app` (the only part `Dockerfile.jvm`
copies) and download it back to `target/quarkus-app`.

### Maven wrapper instead of a hand-installed Maven
`./mvnw versions:set …` and `./mvnw package -DskipTests`. The wrapper is committed and executable;
it downloads the pinned Maven into `~/.m2/wrapper`, which the setup-java cache does not cover, so the
first run per runner downloads Maven once. No `sudo` on the shared runners any more.

### Rejoin counts as activity, GET stays the join
The `game-session` spec makes `GET /api/level/{playerId}` the join on purpose, and the frontend
relies on it; turning it into an explicit `POST /join` would be a contract change in both repos for
no behaviour gain. The real gap is that a rejoin does not refresh `lastActive`. `addBeeIfAbsent`
refreshes `lastActive` on the existing bee as well, which covers `GET /level` and also makes the
redundant `lastActive` write in `updateTarget` harmless (it stays, since steering is the main
activity signal). See `specs/game-session/spec.md`.

### Flower colours from `ColorUtils`
`restartLevel` calls `ColorUtils.pickRandomBaseColor()` instead of the private `pickColor()`. The
palettes are identical apart from the duplicate `salmon`, so `salmon` simply loses its double
chance. `ColorUtils` keeps using `ThreadLocalRandom`; no new `Random` per call.

### Frontend image
`FROM node:24-alpine`; `COPY package.json package-lock.json ./` then `RUN npm ci --omit=dev` (so the
dependency layer is cached across code changes), then `COPY *.js *.css *.html *.mp3 *.png *.jpg
*.ico ./`. `app.js` serves the whole app directory statically, so the image must contain only
runtime files — no `Dockerfile`, `deploy/`, `.github/`. Globs by extension keep new modules working
without editing the Dockerfile. `EXPOSE 8080` stays (compose sets `INTERNAL_PORT=8080`).

### Node 24 everywhere in the frontend
Node 20 is end of life (April 2026). `.nvmrc` → `24`, image `node:24-alpine`; the shared
`npm-build-workflow` follows `.nvmrc`. The app uses only `express` and `import.meta.dirname`
(Node ≥ 20.11), so no code changes.

### Compose service rename
`test` → `frontend`. `container_name: flowers-frontend` stays. `up.sh` runs
`docker-compose up -d --force-recreate --remove-orphans`, which removes the old `test` service
container as an orphan before creating the new one.

## Risks / Trade-offs

- [Service rename: the orphan removal and the new container both use the name
  `flowers-frontend`; if compose created the new one first, it would fail with a name conflict] →
  Compose removes orphans before creating containers; after the deploy, check that
  https://flowers.htl.dev answers. Fallback: `docker rm -f flowers-frontend` and rerun `up.sh` on the
  server.
- [`./mvnw` downloads Maven on every fresh runner] → a few seconds, acceptable; no `sudo`.
- [`npm ci --omit=dev` with an out-of-sync lock file fails in CI] → regenerate `package-lock.json`
  with Node 24 and verify `npm ci` in `node:24` before pushing.
- [Upgraded docker actions behave differently] → same majors already run in the shared
  workflows on the same runners; watch the first pipeline run.

## Migration Plan

Push backend and frontend separately; each push redeploys. Rollback is `git revert` + push. Watch
both pipeline runs and check both sites after deploy.
