## 1. Backend

- [x] 1.1 Add a test in `GameResourceLevelTest`/`LevelServiceCleanupTest` for "Rejoining keeps an idle bee alive" (bee last active 50 s ago, `GET /api/level/{id}`, cleanup at +20 s keeps it) and verify it fails before the fix
- [x] 1.2 In `LevelService.addBeeIfAbsent`, refresh `lastActive` on an existing bee; verify the new test and all existing tests pass (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw test`)
- [x] 1.3 Replace `LevelService.pickColor()` with `ColorUtils.pickRandomBaseColor()` and remove the now unused `java.util.Random` import; verify the tests pass and every generated flower `color` is a key of `ColorUtils.getNamedColors()` (assert in `GameResourceLevelTest`)
- [x] 1.4 Translate the German comments in `GameResource` (`// <- Neu: SSE-Event`) and `application.properties` (`# CORS einschalten`) to English; verify with `grep -rnP '[äöüÄÖÜß]|einschalten|Neu:' src/`
- [x] 1.5 Delete `src/main/docker/Dockerfile.native`, `Dockerfile.native-micro`, `Dockerfile.legacy-jar`; set `EXPOSE 8084` in `Dockerfile.jvm`; verify `./mvnw package -DskipTests && docker build -f src/main/docker/Dockerfile.jvm -t flowers-backend:local .` succeeds and the container answers `GET /api/level/x` on 8084

## 2. Frontend

- [x] 2.1 Set `.nvmrc` to `24`; in `package.json` remove `express-session` and move `nodemon` to `devDependencies`; regenerate `package-lock.json` with Node 24 (`docker run --rm -v "$PWD":/app -w /app node:24 npm install`); verify `docker run --rm -v "$PWD":/app -w /app node:24 sh -c "npm ci && npm run build"` passes and `npm ci --omit=dev` installs no `nodemon`
- [x] 2.2 Rewrite `Dockerfile`: `node:24-alpine`, `WORKDIR /app`, copy `package.json`/`package-lock.json`, `npm ci --omit=dev`, then copy runtime files by extension (`*.js *.css *.html *.mp3 *.png *.jpg *.ico`), `EXPOSE 8080`, `CMD ["npm", "run", "prod"]`; verify `docker build` succeeds, the image contains no `Dockerfile`/`deploy/`/`nodemon`, and a container with `INTERNAL_PORT=8080` serves `index.html`, `main.js` and `bee-loop.mp3`
- [x] 2.3 Update the frontend `README.md` requirement line to Node 24; verify by reading it

## 3. Deploy/CI

- [x] 3.1 Backend `pipeline.yml`: checkout v7, setup-java v6 with `cache: maven` (drop `actions/cache`), remove the Maven install step, `./mvnw` for `versions:set` and `package`, upload/download-artifact v7/v8 of `target/quarkus-app`, docker qemu/buildx/login v4 and build-push v7; verify the YAML parses (`python3 -c "import yaml,sys;yaml.safe_load(open(sys.argv[1]))" .github/workflows/pipeline.yml`) and `./mvnw versions:set -DnewVersion=0.0.0 -DgenerateBackupPoms=false` works locally (then `git checkout pom.xml`)
- [x] 3.2 Frontend `deploy/docker-compose.yml`: rename service `test` to `frontend`, keep `container_name` and labels; verify `docker compose -f deploy/docker-compose.yml config` shows service `frontend`

## 4. Docs and memory

- [x] 4.1 Replace "Node 20" with "Node 24" in `CLAUDE.md`, `openspec/config.yaml`, `ai/memory/project_flowers.md`, `ai/memory/reference_machine_jdk.md`, `ai/memory/MEMORY.md` and the `node:20` check in `ai/memory/reference_build_and_test.md`; update `ai/memory/reference_ci_runners.md` (backend pipeline on Node 24 majors, wrapper instead of manual Maven); verify with `grep -rn "Node 20\|node:20" CLAUDE.md openspec/config.yaml ai/memory`
- [x] 4.2 Remove the three entries ("Backend pipeline on old action majors", "Backend small cleanups", "Frontend Dockerfile and dependencies") from `ai/open-proposals.md`

## 5. Verification

- [x] 5.1 Run all backend tests and `http/game.http` against `quarkus:dev` on 8084; drive the frontend headless (Playwright) against the local backend and the locally built frontend image: page loads, bee appears, harvest works; stop all servers/containers started and check with `ss`/`docker ps`
- [ ] 5.2 After push (during archive): watch both pipeline runs to green and check that https://flowers.htl.dev and https://flowers-backend.htl.dev/api/level/check answer
