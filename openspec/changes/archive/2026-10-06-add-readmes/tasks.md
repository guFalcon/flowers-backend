## 1. Backend

- [x] 1.1 Write `docs/diagrams/architecture.puml` (component overview: browser, Express static server, Quarkus backend with REST + SSE, Traefik in production), render `architecture.svg` via plantuml.unterrainer.info and verify it visually (render to PNG and view)
- [x] 1.2 Write `docs/diagrams/game-round.puml` (sequence: two players join via `GET /api/level/{playerId}`, `POST /api/player/{id}/target`, periodic and triggered `level-update`, `POST /api/harvest/{flowerId}`, admin restart + `levelRestarted`), render `game-round.svg` and verify it visually
- [x] 1.3 Rewrite `README.md`: overview, links (frontend repo, live URLs), game mechanics with the actual timings, embedded diagrams, verify every number against `LevelService`/`application.properties`
- [x] 1.4 Add the REST API table (all five endpoints, request/response shapes) and SSE event section (`level-update`, `harvest`, `levelRestarted` with fields, double-encoded `level` stated as-is); verify against `GameResource` and a live `curl` against `quarkus:dev`
- [x] 1.5 Add local development (dev mode, Swagger UI, CORS/port), image build, pipeline/deploy and repo layout sections; verify `./mvnw quarkus:dev` starts on 8084 and `/q/swagger-ui` responds, then stop the server

## 2. Frontend

- [x] 2.1 Rewrite `flowers-frontend/README.md`: overview, how to play, admin mode `?admin=true`, embedded backend diagrams via GitHub raw URLs, link to the backend README contract section
- [x] 2.2 Add local development (`npm ci && npm start` on 8081, switching `SERVER` to `http://localhost:8084`), file roles, Docker/pipeline/deploy sections; verify `npm ci && npm start` serves `index.html` on 8081, then stop the server

## 3. Verification

- [x] 3.1 Check both READMEs for broken relative links and that every command shown was run successfully during 1.5/2.2; confirm with `ps`/`ss` that no server started during apply is still running
