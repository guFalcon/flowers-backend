## Context

See proposal.md for the motivation. Current state that shapes the approach:

- `LevelService` owns all state under its monitor; SSE events are published after the lock is
  released. Bees are `Bee` DTOs that are serialised directly (deep copies per snapshot).
- `Bee.x/y` is random at registration and never updated; `targetX/targetY` is set by
  `POST /player/{id}/target`. The flight exists only in `bee.js`: duration
  `max(dist × 5 s, 0.2 s)` with `dist` in relative coordinates, starting from the position the bee
  is drawn at.
- The client starts its harvest timer after the target POST has been answered, i.e. after the
  server has started the flight, so it normally fires slightly *after* the server-side arrival.
- `cleanupInactiveBees(long now)` already shows the pattern for testable time: a public scheduled
  method plus a package-private overload taking `now`.
- Deployment: the shared `UnterrainerInformatik/deploy-workflow` checks out the repo, writes
  `deploy/.env` itself (version, image name) and copies `deploy/` to the host. A caller cannot add
  variables today.

## Goals / Non-Goals

**Goals:**
- Server-side position computable in O(1) at any instant, no tick loop.
- Every time-dependent rule testable without sleeping.
- Admin protection that fails closed and needs no new dependency.

**Non-Goals:**
- Exact agreement between the animated position in the browser and the server position during a
  flight; only the start, the target and the arrival time need to match.

## Decisions

### D1 — Flight as a stored segment, position computed on demand
`Bee` gets `fromX`, `fromY`, `flightStart`, `flightEnd` (epoch millis), all `@JsonIgnore`, and
`honey` (`long`). `positionAt(now)` interpolates linearly between `from` and `target` over
`[flightStart, flightEnd]` and returns the target afterwards. `setTarget` first freezes the current
position into `from`, then sets the new target, `flightStart = now`,
`flightEnd = now + max(5000 × d, 200)`.
`copyLevel(now)` writes `positionAt(now)` into the copies' `x`/`y`, so the JSON contract keeps its
shape (`x`, `y`, `targetX`, `targetY`, `color`, plus the new `honey`).

*Alternative:* a scheduled tick that advances every bee — rejected: costs work for idle bees, adds
jitter and makes tests timing-dependent.

### D2 — Time as a parameter
Public methods use `System.currentTimeMillis()` and delegate to package-private overloads that take
`now` (`registerBee`, `setTarget`, `harvest`, level snapshot). Tests call the overloads directly;
REST tests only cover the wiring.

### D3 — Harvest validation on the server, triggered by the client
`POST /api/player/{playerId}/harvest`: under the lock, look up the bee (404 if absent); if
`now < flightEnd − 500` → `gained` 0; else take the bee's target as position and pick the closest
flower with `hypot((fx − bx) × 9/16, fy − by) ≤ 0.175 × size`; if its fill > 0.1, yield
`round(fill² × 1000)`, empty it, add to `honey`. Publish `harvest` after releasing the lock (as
today).

Response shape:
```json
{ "flowerId": "flower-3", "gained": 250, "total": 650 }
{ "flowerId": null,       "gained": 0,   "total": 650 }
```
SSE `harvest` event unchanged: `{"type":"harvest","flowerId":"flower-3","fill":0}`.
Level bee shape: `{"id":"p1","x":0.4,"y":0.5,"targetX":0.6,"targetY":0.5,"color":"#…","lastActive":…,"honey":650}`.

*Alternative:* the server harvests automatically on arrival — rejected: needs per-bee timers and a
new event to deliver the bump/slurp feedback to the right client; the client-triggered request
keeps the existing feedback flow and is just as tamper-proof, because the server decides.
*Alternative:* client still sends the flower id and the server only checks it — rejected: redundant
and lets the client probe flowers.

### D4 — Admin token check as a request filter
`AdminTokenFilter` (`@ServerRequestFilter` in `resources/`) handles every request whose path starts
with `/api/admin/` and aborts with `403` unless `X-Admin-Token` equals the configured token
(`MessageDigest.isEqual` on UTF-8 bytes). Config property `flowers.admin-token` with default empty,
bound from the env var `FLOWERS_ADMIN_TOKEN` via `application.properties`
(`flowers.admin-token=${FLOWERS_ADMIN_TOKEN:}`). Blank token ⇒ always 403. CORS:
`quarkus.http.cors.headers=accept,content-type,x-admin-token`. Tests use a `%test` profile value.

*Alternative:* check inside `restartLevel()` — rejected: every future admin endpoint would have to
remember it.

### D5 — Token delivery to the container
- Generate a random token (32 bytes, URL-safe base64) into `ai/secrets/flowers-admin-token`.
- `deploy-workflow`: new optional secret `EXTRA_ENV`; after "Fill .env file" a step appends it
  verbatim (`printf '%s\n' "$EXTRA_ENV" >> ./deploy/.env`) if non-empty. Purely additive; other
  callers are unaffected.
- Backend pipeline passes `EXTRA_ENV: FLOWERS_ADMIN_TOKEN=${{ secrets.FLOWERS_ADMIN_TOKEN }}`.
- GitHub repo secret `FLOWERS_ADMIN_TOKEN` on `guFalcon/flowers-backend`, set via `gh secret set`
  from the file in `ai/secrets/`.
- `deploy/docker-compose.yml`: `- FLOWERS_ADMIN_TOKEN=${FLOWERS_ADMIN_TOKEN}`.

*Alternatives:* bake the token into the image — rejected (image is on Docker Hub); a hand-maintained
env file on the host — rejected (not reproducible, invisible in the repo).

### D6 — Frontend
- `bee.js`: `placeAt(x, y)` sets position without transition/sound. `bees.js`: a newly created
  instance gets `placeAt(b.x, b.y)`, then `moveTo(target)` if the target differs by more than 0.001;
  existing instances keep today's target comparison.
- `harvest.js`: after the local flight timer (unchanged, still guarded by `flightId`), POST
  `HARVEST_URL(PLAYER_ID)`; slurp + `total` if `gained > 0`, else bump. The DOM search for the
  closest `.center` and `state.userHoney` go away; the response's `flowerId`/`fill` update is not
  needed, because the `harvest` SSE event already updates the flower for every client.
- `level.js`: after `init()` and `applyLevel()` the honey display shows the own bee's `honey`
  (a small `showHoney(level)` helper in `harvest.js` or a new `honey.js`).
- `admin.js`: `adminToken = params.get("admin")`; panel shown if non-empty; restart sends
  `X-Admin-Token`; on 403 the panel shows "Admin token rejected".

## Risks / Trade-offs

- [The browser animates from its drawn position, the server from its computed one; after a redirect
  mid-flight they can differ slightly] → both end at the same target; the 500 ms tolerance and
  "position = target once arrived" make the harvest robust; the next `level-update` re-syncs.
- [A `level-update` built just before a harvest can arrive after the harvest response and briefly
  show the old honey] → corrected by the next update within 3 s; acceptable.
- [The token in the admin URL can end up in browser history] → acceptable for a classroom game; the
  admin URL is only used on the teacher's machine. Rotate by changing the secret and redeploying.
- [Change to a shared workflow used by other repos] → the new secret is optional and the step is
  skipped when it is empty.
- [Old frontends (cached tabs) call the removed harvest endpoint] → they get 404 and play the bump
  sound; a reload fixes it.

## Migration Plan

1. Generate the token, set the GitHub secret, extend and push `deploy-workflow`.
2. Push backend and frontend together (backend first). Until the frontend is redeployed, harvesting
   from an old page fails harmlessly.
3. Verify live: restart without token → 403; with `?admin=<token>` → works.

Rollback: revert the commits in both repos; the extra `.env` line is harmless for the old backend.
