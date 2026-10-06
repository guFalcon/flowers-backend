## Why

Neither repo explains itself: `flowers-backend/README.md` is the unmodified Quarkus scaffold (and
names the wrong port, 8080), `flowers-frontend/README.md` is a single heading. Students and new
contributors have to read the code to learn what the game does, how the two parts talk to each
other and how to run them locally.

## What Changes

- Replace `flowers-backend/README.md` with a project README: what flowers is, links to the frontend
  repo and the live sites, game mechanics (flower fill tick, inactive-bee cleanup, periodic level
  broadcast), the REST API (all five endpoints with request/response shapes), the SSE events
  (`level-update`, `harvest`, `levelRestarted`) and their fields, local development (port 8084,
  Swagger UI, CORS), image build, pipeline and deployment, and the repo layout (`src/`, `deploy/`,
  `openspec/`, `http/`, `ai/`, `docs/`).
- Replace `flowers-frontend/README.md` with a project README: what it is, how to play, admin mode
  (`?admin=true`), local development (port 8081, pointing `SERVER` at a local backend), the role of
  each file, a short protocol summary that defers to the backend README, Docker image, pipeline and
  deployment.
- Add PlantUML diagrams (`.puml` source + rendered `.svg`) under `flowers-backend/docs/diagrams/`:
  a component overview (browser, Express static server, Quarkus backend, Traefik) and a sequence
  diagram of a game round (join → set target → SSE `level-update` → harvest → restart).
- Both READMEs describe the code as it is today. Known defects (listed in `ai/open-proposals.md`)
  are not presented as intended behaviour; where the README touches them it states the current
  behaviour neutrally.

## Non-goals

- No code, configuration, REST/SSE contract, Dockerfile or pipeline change in either repo — the
  defects found during the review stay in `ai/open-proposals.md` for their own changes.
- No `http/` request files (they come with the first REST change).
- No German translation; the READMEs are English like the rest of the repos.

## Capabilities

### New Capabilities
- none — documentation only; `skip_specs: true` is set in `.openspec.yaml`.

### Modified Capabilities
- none

## Impact

- **Backend repo:** `README.md` rewritten; new `docs/diagrams/architecture.{puml,svg}` and
  `docs/diagrams/game-round.{puml,svg}`.
- **Frontend repo:** `README.md` rewritten; it embeds the backend's diagrams via their GitHub URLs
  (both repos are public).
- **Deploy/CI:** none functionally, but pushing either `main` triggers the pipeline and redeploys the
  unchanged application (new patch version).
