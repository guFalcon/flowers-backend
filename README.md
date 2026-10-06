# flowers-backend

Backend of **flowers**, a small multiplayer browser game used for teaching at HTL: every player
steers a bee across a meadow and harvests honey from flowers. All players share one level; every
change is pushed to all browsers via Server-Sent Events (SSE).

- Frontend: [guFalcon/flowers-frontend](https://github.com/guFalcon/flowers-frontend)
- Live game: <https://flowers.htl.dev> (admin view: <https://flowers.htl.dev/?admin=true>)
- Live backend: <https://flowers-backend.htl.dev>

Java 21, [Quarkus](https://quarkus.io/) (REST + Jackson, Mutiny, Scheduler), Lombok. All game state
lives in memory — there is no database, and a restart of the backend starts a fresh level.

![Component overview](docs/diagrams/architecture.svg)

## Game mechanics

- A **level** is a 9:16 meadow with 6–11 randomly placed **flowers**. Positions and sizes are
  relative (`0..1`) so every screen size shows the same level.
- Each flower has a nectar `fill` (`0..1`) that grows by its own `rate` **every second**, up to 1.
- A **bee** is created for every player id the first time the player loads the level or sets a
  target. It gets a random colour.
- Players move their bee by setting a **target**; the browser animates the flight. Other players
  see the new target with the next `level-update`.
- **Harvesting** a flower with `fill > 0.1` yields `fill² × 100` honey and empties the flower;
  with `fill ≤ 0.1` it yields 0. The honey total is kept by the browser, not by the server.
- Every **10 seconds** bees whose player has not set a target for **60 seconds** are removed.
- The whole level is broadcast **every 3 seconds** and additionally whenever a bee is added,
  moved or removed.
- An **admin restart** generates new flowers; the bees stay.

![One game round](docs/diagrams/game-round.svg)

## REST API

All endpoints live under `/api`, send and receive JSON. There is no authentication.

| Method | Path | Body | Response | Effect |
|---|---|---|---|---|
| `GET` | `/api/level/{playerId}` | – | `Level` with `yourBeeId` | Registers a bee for `playerId` if unknown, returns the current level |
| `POST` | `/api/player/{playerId}/target` | `{"x": 0.5, "y": 0.5}` | `{"status": "ok"}` | Sets the bee's target (registers the bee if unknown), broadcasts `level-update` |
| `POST` | `/api/harvest/{flowerId}` | – | `{"flowerId": "flower-0", "honey": 100.0}` | Harvests the flower, see [Game mechanics](#game-mechanics) |
| `POST` | `/api/admin/restart` | – | `{"status": "ok", "message": "Level restarted"}` | New flowers, broadcasts `levelRestarted` |
| `GET` | `/api/events` | – | SSE stream (`text/event-stream`) | See [SSE events](#sse-events) |

`playerId` is any string; the frontend uses a UUID kept in `localStorage`. An unknown `flowerId`
yields `honey: 0`.

`Level`:

```json
{
  "aspect": "9:16",
  "flowers": [
    {
      "id": "flower-0", "x": 0.98, "y": 0.01, "size": 0.087,
      "petals": 5, "color": "lightgreen",
      "petalColors": ["#A0FAA2", "#92DD8D", "#9FF1A2", "#8FDF86", "#7EE188"],
      "stampColor": "#7BD97E", "fill": 1.0, "rate": 0.033
    }
  ],
  "bees": [
    {
      "id": "3f0c…", "x": 0.41, "y": 0.77, "targetX": 0.5, "targetY": 0.5,
      "color": "#E6A4D9", "lastActive": 1791273600000
    }
  ],
  "yourBeeId": "3f0c…"
}
```

`x`, `y`, `size`, `targetX`, `targetY` are relative to the play area (`size` relative to its
height); `rate` is the fill increase per second. `x`/`y` of a bee are its spawn position — the
server only tracks targets. `yourBeeId` is set only in the response of `GET /api/level/{playerId}`.

## SSE events

`GET /api/events` keeps the connection open and sends one JSON object per `data:` line. The `type`
field tells the events apart:

| `type` | Fields | When |
|---|---|---|
| `level-update` | `level`: the `Level` (without `yourBeeId`) **as a JSON string** — parse it a second time | Every 3 s, and when a bee is added, moved or removed |
| `harvest` | `flowerId` | Currently sent when a harvest yields **no** honey (also for unknown flower ids) — see `ai/open-proposals.md` |
| `levelRestarted` | – | After `POST /api/admin/restart`; clients reload the level |

```text
data:{"type":"levelRestarted"}
data:{"type":"harvest","flowerId":"flower-3"}
data:{"type":"level-update","level":"{\"aspect\":\"9:16\",\"flowers\":[…],\"bees\":[…]}"}
```

Watch the stream by hand with `curl -N http://localhost:8084/api/events`.

## Local development

Requirements: JDK 21 (Lombok 1.18.38 does not compile on newer JDKs — set `JAVA_HOME` to a JDK 21
if your default is newer) and Docker only for image builds. Maven comes with the wrapper.

```shell
./mvnw quarkus:dev
```

> `mvnw` is currently not marked executable in git. If `./mvnw` reports "permission denied",
> run `sh mvnw quarkus:dev` (or `chmod +x mvnw`).

- Backend: <http://localhost:8084>
- Swagger UI: <http://localhost:8084/q/swagger-ui/> (OpenAPI document at `/q/openapi`)
- Dev UI: <http://localhost:8084/q/dev-ui/>

Configuration is in `src/main/resources/application.properties`:

| Property | Value | Purpose |
|---|---|---|
| `quarkus.http.port` | `8084` | HTTP port |
| `quarkus.http.cors.origins` | `https://flowers.htl.dev, http://localhost:8080, http://localhost:8081` | Browser origins allowed to call the API — add yours when the frontend runs elsewhere |

To play against the local backend, run the frontend on port 8081 and point its `SERVER` constant
at `http://localhost:8084` (see the frontend README).

There are no automated tests yet (`src/test` does not exist).

## Build and deployment

```shell
./mvnw package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t flowers-backend:local .
docker run --rm -p 8084:8084 flowers-backend:local
```

Every push to `main` runs `.github/workflows/pipeline.yml`:

1. bump the semantic version (shared `bump-semver-workflow`),
2. `mvn package -DskipTests` on a self-hosted runner (tests are not run in CI),
3. build the image from `src/main/docker/Dockerfile.jvm` and push it to Docker Hub as
   `gufalcon/flowers-backend:latest`,
4. deploy with the shared `deploy-workflow`, which runs `deploy/up.sh` (`docker-compose pull && up`)
   on the server. `deploy/docker-compose.yml` puts the container into the external Traefik network
   `proxy_default` and routes `flowers-backend.htl.dev` to port 8084.

## Repository layout

| Path | Content |
|---|---|
| `src/main/java/info/unterrainer/htl/resources/GameResource.java` | REST and SSE endpoints |
| `src/main/java/info/unterrainer/htl/services/LevelService.java` | Level, bees, harvesting, scheduled fill / cleanup / broadcast |
| `src/main/java/info/unterrainer/htl/services/EventBusService.java` | Fan-out of events to all SSE clients |
| `src/main/java/info/unterrainer/htl/dtos/` | `Level`, `Flower`, `Bee` |
| `src/main/java/info/unterrainer/htl/ColorUtils.java` | Flower and bee colours |
| `src/main/docker/` | Dockerfiles (`Dockerfile.jvm` is the one CI uses) |
| `deploy/` | docker compose file and `up.sh` used by the deploy workflow |
| `docs/diagrams/` | PlantUML sources (`.puml`) and rendered SVGs |
| `openspec/` | [OpenSpec](https://github.com/Fission-AI/OpenSpec) specs and changes for **both** repos |
| `http/` | `.http` request files for the REST API (added with the first REST change) |
| `ai/` | Working notes for the AI assistant: memory and the backlog `ai/open-proposals.md` |

Diagrams are rendered with:

```shell
curl -sS -L -X POST -H 'Content-Type: text/plain; charset=utf-8' \
  --data-binary @docs/diagrams/game-round.puml \
  https://plantuml.unterrainer.info/plantuml/svg -o docs/diagrams/game-round.svg
```
