## Why

The backend trusts the client for everything that matters: it never knows where a bee actually is
(`Bee.x/y` is set once at registration, the flight happens only in the browser), so any client can
harvest any flower from anywhere; the honey score lives only in the browser and is lost on reload;
and `POST /api/admin/restart` is open to anyone (the frontend "admin mode" is just `?admin=true`).
A live leaderboard is planned next and needs a score the server owns. In addition, a newly joined
bee visibly flies from the middle of the meadow to its start position instead of appearing there.

## What Changes

- The server simulates every flight: setting a target records start position, target, start time
  and arrival time (same speed formula as the frontend), so the server knows each bee's current
  position at any time. `x`/`y` in the level payload become the real current position.
- **BREAKING** `POST /api/harvest/{flowerId}` is removed and replaced by
  `POST /api/player/{playerId}/harvest`. The server harvests the flower under the player's bee, and
  only if the bee has arrived; otherwise the player gets 0 honey.
- The server keeps each bee's honey (in the points the player sees). The harvest response carries
  `gained` and `total`; every bee in the level carries `honey`. The frontend shows its own bee's
  honey from the server, so the score survives a reload. A level restart resets all honey to 0.
- **BREAKING** `POST /api/admin/restart` requires the header `X-Admin-Token` matching the env var
  `FLOWERS_ADMIN_TOKEN`; without a configured token the endpoint always answers `403`. The admin
  page is opened with `?admin=<token>` and sends the header.
- A new bee spawns standing still: its target equals its start position, and the frontend places a
  newly created bee directly at its server position without a flight animation.
- Deployment passes `FLOWERS_ADMIN_TOKEN` to the backend container via a new optional
  `EXTRA_ENV` secret of the shared `deploy-workflow`.

## Non-goals

- No player names, no leaderboard UI (separate backlog entry; this change only provides the data).
- No authentication of players: a `playerId` is still a client-chosen UUID; anyone knowing another
  player's id could steer that bee. Protecting player identity is out of scope.
- No server-side collision, obstacles or clouds; flights stay straight lines at constant speed.
- No persistence: honey lives in memory and disappears with the bee after 60 s of inactivity or a
  backend restart.
- No change to flower growth or the harvest yield formula.

## Capabilities

### New Capabilities
- `admin-access`: protection of admin endpoints by a shared admin token, and how the frontend
  admin mode obtains and sends it.

### Modified Capabilities
- `flower-harvest`: harvesting moves to a per-player endpoint, is validated against the bee's
  server-side position and arrival, and adds the honey to the bee's server-side score.
- `game-session`: server-side flight simulation (target sets a flight, level shows the current
  position), honey per bee in the level, spawn without flight, restart resets honey and requires the
  admin token.

## Impact

- **Backend:** `GameResource` (new harvest endpoint, removed old one, admin token check),
  `LevelService` (flight simulation, position-based harvest, honey, spawn, restart reset), `Bee` DTO
  (flight fields, `honey`), `application.properties` (CORS header, token config), new and updated
  tests, `http/game.http`.
- **Frontend:** `config.js` (harvest URL), `harvest.js` (no DOM flower search, new endpoint, honey
  from response), `state.js` (`userHoney` removed), `bees.js`/`bee.js` (spawn at position, honey
  display from level), `admin.js` (token from `?admin=`, header).
- **Deploy/CI:** `deploy/docker-compose.yml` of the backend passes `FLOWERS_ADMIN_TOKEN`;
  `UnterrainerInformatik/deploy-workflow` gets an optional `EXTRA_ENV` secret appended to
  `deploy/.env`; the backend pipeline passes it; GitHub secret `FLOWERS_ADMIN_TOKEN` on
  `guFalcon/flowers-backend`. Token copy in `ai/secrets/`.
- **Contract:** breaking for old frontends (harvest URL, admin header); backend and frontend deploy
  together.
