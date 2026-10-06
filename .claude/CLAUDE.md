# CLAUDE Project — flowers

## Working rules
- Memory is in `./ai/memory/MEMORY.md` (index) with one `.md` file per entry under `./ai/memory/`. Treat that index as the auto-memory; keep it in sync the same way you would the per-machine `~/.claude/projects/<cwd>/memory/MEMORY.md`. **Read it at the start of every session.**
- **Conversation language is German.** Always reply to the user in German — every response, from the first message of a session on, without waiting to be reminded. Everything written into the repos (identifiers, comments, log messages, docs, commit messages, OpenSpec artifacts) stays English.
- **No change outside an OpenSpec change** — code, docs and project configuration alike, in both repos. Announce first, then `/opsx:propose`, implement via `/opsx:apply`. A diagnosis request means diagnose only. See `ai/memory/feedback_announce_changes_first.md` and `ai/memory/feedback_openspec_only_changes.md`.
- Backlog of drafted-but-not-proposed work lives in `./ai/open-proposals.md`.
- A REST/SSE contract change covers backend and frontend in the same change (`ai/memory/feedback_contract_both_repos.md`).
- Secrets go to `./ai/secrets/` (git-ignored), never into tracked files.

## Repositories — one Claude for both
flowers is a small multiplayer browser game (bees fly to flowers and harvest honey), used for teaching at HTL. It is split into two repos; **this backend repo is the control centre** — memory, OpenSpec and backlog live here only, and Claude drives the frontend repo from here.

- **Backend — this repo** (`~/source/htl/java/flowers-backend`, GitHub `guFalcon/flowers-backend`, branch `main`): Java 21, Quarkus (Maven wrapper), package `info.unterrainer.htl`. REST + SSE under `/api` (`resources/GameResource.java`), in-memory game state (`services/LevelService`, `services/EventBusService`), no database. Port 8084. Tests: JUnit 5 + AssertJ (`@QuarkusTest`, REST Assured); no tests exist yet.
- **Frontend** (`~/source/htl/js/flowers-frontend`, GitHub `guFalcon/flowers-frontend`, branch `main`): plain HTML/CSS/JS as native ES modules (`index.html` loads `main.js`; one module per concern, plus the classes in `bee.js`, `sse-connection.js`, `audio-system.js`) served statically by a tiny Express server (`app.js`, port 8081, Node 20 per `.nvmrc`). No build step. Backend base URL is the `SERVER` constant in `config.js`.

Layout of this repo:
- `src/` — Quarkus application; `src/main/docker/` — Dockerfiles (`Dockerfile.jvm` is used by CI).
- `deploy/` — docker compose + `up.sh` used by the deploy workflow (both repos have one).
- `openspec/` — specs and changes for **both** repos; one spec tree.
- `http/` — `.http` request files exercising the backend REST API (created with the first REST change).
- `ai/` — memory, open proposals, secrets (git-ignored). Not shipped.

## Deployment
Every push to `main` of either repo runs its GitHub pipeline (bump → build → Docker Hub image `gufalcon/flowers-*:latest` → `deploy-workflow`) and redeploys the live site behind Traefik: frontend `https://flowers.htl.dev`, backend `https://flowers-backend.htl.dev`. CI runs no tests. See `ai/memory/project_flowers.md`.

Build/test commands are recorded in memory (`reference_build_and_test.md`).
