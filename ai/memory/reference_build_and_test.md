---
name: reference_build_and_test
description: Dev/test/build commands for flowers-backend (Maven/Quarkus) and flowers-frontend (Express), .http files and headless-browser checks
metadata:
  type: reference
---

**Not yet verified for flowers** (taken from the READMEs/configs on 2026-10-05) unless marked
otherwise — mark each line verified once it has run.

- **Backend dev:** `./mvnw quarkus:dev` → http://localhost:8084 (`quarkus.http.port=8084`), Dev UI
  at `/q/dev/`, Swagger UI via smallrye-openapi at `/q/swagger-ui`. No Dev Services (no database).
- **Backend tests:** `./mvnw verify`; single class `./mvnw test -Dtest=GameResourceTest`. No
  `src/test` exists yet. `@QuarkusTest` binds 8081 by default — that clashes with the frontend dev
  server; stop it first or set `quarkus.http.test-port`.
- **Backend image:** `./mvnw package -DskipTests && docker build -f src/main/docker/Dockerfile.jvm -t flowers-backend:local .`
- **Frontend dev:** `cd ~/source/htl/js/flowers-frontend && npm ci && npm start` (nodemon) →
  http://localhost:8081 (`INTERNAL_PORT` overrides). For a local backend switch the `SERVER`
  constant in `index.html` or reroute in Playwright (preferred: no file change). CORS already
  allows `http://localhost:8080` and `:8081`.
- **Frontend CI check:** the shared `npm-build-workflow` runs `npm ci`, so `package-lock.json` must
  stay in sync with `package.json`; verify with `docker run --rm -v "$PWD":/app -w /app node:20 sh -c "npm ci && npm run build"`.
- **.http files (from presserl, verified there):** `cd http && docker run --rm --network host -v "$PWD":/workdir
  jetbrains/intellij-http-client --env-file http-client.env.json --env dev *.http`;
  `-V baseUrl=…` overrides the env file. `client.test(...)` callbacks run after the handler body,
  so read a global into a `const` before a later `client.global.set` overwrites it.
- **Headless browser (from presserl, verified there):** `mcr.microsoft.com/playwright:v1.55.0-noble`,
  `npm i playwright@1.55.0` in a scratch dir, `docker run --rm --network host -v <dir>:/work -w /work
  mcr.microsoft.com/playwright:v1.55.0-noble node script.js`. The game renders as plain DOM elements
  (no canvas), so normal locators work; use screenshots plus the backend's API/SSE to judge state. Audio needs a user
  gesture — click once before expecting sound-dependent code paths.
- **SSE by hand:** `curl -N http://localhost:8084/api/events`.

See [[reference_machine_jdk]].
