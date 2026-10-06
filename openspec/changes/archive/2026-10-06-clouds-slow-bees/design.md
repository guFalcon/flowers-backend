## Context

See proposal.md for the motivation and specs/ for the required behaviour. Today a flight is a single
straight segment: `Bee` stores `fromX/fromY/flightStart/flightEnd` (JSON-ignored), `positionAt`
interpolates linearly, and the frontend independently computes the same duration
(`Bee.getTravelDurationInMillis` in `bee.js`) to drive a CSS `left/top` transition and the own
harvest timer. All game state lives in `LevelService` under its monitor; tests drive it through
package-private overloads that take `now`.

## Goals / Non-Goals

**Goals:**
- One deterministic cloud model, implemented identically in Java and JavaScript, so a client never
  needs more than the latest level to draw clouds where the server sees them.
- The server is the only place that knows how long a flight takes.
- Existing tests keep working with minimal changes (clouds can be switched off in tests).

**Non-Goals:**
- Exact latency compensation of the client clock offset.
- Sub-step accuracy of cloud entry/exit times beyond the 0.1 s the spec requires.

## Decisions

### Contract shapes

`GET /api/level/{playerId}` and the `level` of `level-update` (new fields marked `+`):

```json
{
  "aspect": "9:16",
+ "serverTime": 1791280000000,
  "flowers": [ ... unchanged ... ],
+ "clouds": [
+   { "id": "cloud-0", "x": 0.42, "y": 0.17, "t": 1791280000000,
+     "size": 0.10, "speed": 0.031, "drift": -0.21 }
+ ],
+ "wind": [
+   { "t": 1791280000000, "angle": 0.52 },
+   { "t": 1791280017000, "angle": 0.61 },
+   { "t": 1791280017500, "angle": 0.70 }
+ ],
  "bees": [
    { "id": "p1", "x": 0.4, "y": 0.5, "targetX": 0.6, "targetY": 0.5,
      "color": "#…", "lastActive": 1791280000000, "honey": 0, "name": "Flip",
+     "path": [ { "t": 1791279999000, "x": 0.2, "y": 0.5 },
+               { "t": 1791280001000, "x": 0.6, "y": 0.5 } ] }
  ],
  "yourBeeId": "p1"
}
```

`POST /api/player/{id}/target` → `200`:

```json
{ "status": "ok",
  "path": [ { "t": 1791280000000, "x": 0.20, "y": 0.5 },
            { "t": 1791280000555, "x": 0.311, "y": 0.5 },
            { "t": 1791280003095, "x": 0.489, "y": 0.5 },
            { "t": 1791280003650, "x": 0.60, "y": 0.5 } ] }
```

Harvest request/response and the `harvest` / `levelRestarted` events are unchanged. All additions
are additive; old clients ignore them.

Angles are radians, 0 = towards +x, π/2 = towards +y (screen down). Cloud `speed`/`size` are in
play-area heights (per second); `x` positions are in widths, so a horizontal movement of `d` heights
changes `x` by `d / (9/16)`. Bee speed stays in raw relative units (0.2/s), exactly as today.

### Backend model

- New DTOs in `dtos/`: `Cloud` (Lombok `@Data @Builder`, like `Flower`), records `WindKeyframe(t,
  angle)` and `PathKeyframe(t, x, y)`. `Level` gains `serverTime`, `clouds`, `wind`.
- New pure class `services/Weather` (no CDI, no locking — always used under the `LevelService`
  lock): holds the cloud list and the wind keyframes and offers
  `Bee.Position cloudPositionAt(Cloud c, long t)`, `boolean inCloud(double x, double y, long t)`,
  `void ensureHorizon(long now, Random r)` and `static Weather generate(long now, Random r)` /
  `Weather.none()` (no clouds, for tests). Cloud position = anchor + Σ over wind segments between
  `c.t` and `T` of `speed·Δt·(cos(a+drift)/(9/16), sin(a+drift))`, then wrap with
  `((p − min) mod span + span) mod span + min`. Kept in its own class so it can be unit-tested
  without Quarkus and mirrored 1:1 in `clouds.js`.
- **Wind generation**: starting from a random angle, alternate "hold" segments (15–30 s minus the
  turn, as one keyframe) and "turns" (total Δ uniform in ±60°, duration 5–8 s, split into
  `ceil(duration / 0.5 s)` equal steps, one keyframe each). `ensureHorizon(now)` appends until the
  last keyframe is ≥ now + 90 s whenever it is < now + 75 s (so ≥ 60 s holds between refreshes), and
  prunes: every cloud is re-anchored to `now` (its computed position, `t = now`), wind keyframes
  before `now` are dropped except the one in effect, whose `t` becomes `now`. Re-anchoring does not
  change any future cloud position, so paths computed earlier stay valid and clients holding an older
  level still compute the same positions.
- `copyLevel` (already under the lock and used by every level a client gets) calls
  `ensureHorizon(now)` first and copies `serverTime = now`, clouds and wind. This guarantees the
  60 s horizon in every published level without an extra scheduler.
- **Flight simulation** in `updateTarget`: start at `positionAt(now)`, direction unit vector to the
  target, simulate in 50 ms steps: at each step evaluate `inCloud` at the current position/time,
  advance `speed·dt` (0.2 or 0.07 units/s); when the in-cloud state at the step's end differs from
  the current one, locate the entry/exit within the step by bisection (10 halvings, < 0.1 ms), emit a
  keyframe there and continue from it at the other speed. The final step is clamped to the remaining
  distance and its exact fractional duration is used for the arrival keyframe. If the plain duration
  is below 200 ms the path is just `[start@now, target@now+200]` (as today, no cloud check).
  Detecting the change only at step boundaries would put up to 0.01 units of a flight on the wrong
  side of the cloud edge (up to ~90 ms error), too close to the spec's 100 ms; the bisection removes
  that error except for clouds passing completely within one step.
  *Alternative considered*: analytic circle–line intersection. Exact for resting clouds, but drifting
  clouds whose velocity changes with the wind and wrap make it piecewise and error-prone; the step
  simulation is simple, robust and good enough. Flights are short (max ~20 s → 400 steps × 6 clouds).
- `Bee`: `fromX/fromY/flightStart/flightEnd` are replaced by `List<PathKeyframe> path` (serialised,
  immutable `List.copyOf`). `positionAt` finds the segment and interpolates; a new `@JsonIgnore`
  `arrivalTime()` returns the last keyframe's `t` and replaces `flightEnd` in the harvest check.
  New bees get a one-keyframe path. `toBuilder()` copies share the immutable list, which is safe.
- `setTarget` returns the new path (`updateTarget` returns it); `GameResource.setTarget` answers
  `Map.of("status", "ok", "path", path)`.
- `restartLevel` regenerates the clouds only (`weather.regenerateClouds(now)`), keeping the wind.
- Tests: `LevelService` gets a package-private `useWeather(Weather w)`. Existing flight/harvest tests
  call `useWeather(Weather.none())` (clouds would make their timings random). `placeArrived` moves
  its start to 30 s ago, since a slowed diagonal flight can take ~20 s. Cloud tests build a `Weather`
  with chosen clouds (speed 0, or known drift) and a constant wind.

### Frontend model

- `clock.js` (new): `setServerTime(serverTime)` stores `offset = serverTime − Date.now()`;
  `serverNow()` returns `Date.now() + offset`. Called in `level.js` for `GET` and `level-update`.
- `clouds.js` (new): `cloudPositionAt(cloud, wind, t)` mirrors `Weather` exactly (same constants);
  `renderClouds(level)` replaces the cloud elements (`div.cloud`, soft radial gradient + blur,
  `pointer-events: none`, z-index above the bees) and stores the cloud/wind data; one
  `requestAnimationFrame` loop positions them at `serverNow()`.
- `bee.js`: the CSS transition and `getTravelDurationInMillis`/`baseSpeed` go away. `setPath(path)`
  stores the keyframes and starts a rAF loop that places the bee at its path position for
  `serverNow()`, adding the existing jitter while it moves; the loop (and the flight sound, as today
  started on a new moving path and stopped on arrival) ends after the last keyframe. `placeAt` stays
  for standing bees. The `.bee-tint-wrapper` CSS transition on `left/top` is removed.
- `bees.js`: for each bee, `setPath(b.path)` when the bee is new or its path differs (compare the
  last keyframe's `t/x/y`); a new bee is thus placed where the server says without flying in.
- `harvest.js`: on click, increment the flight id, `POST` the target, then
  `myBee.setPath(json.path)` and `setTimeout(harvest, lastKeyframe.t − serverNow())` guarded by the
  flight id. The own bee starts moving one round trip after the click instead of immediately — an
  accepted trade-off for having one source of truth (typically < 100 ms).

## Risks / Trade-offs

- [Java and JS cloud math drift apart] → Same formula and constants in both, a backend test with a
  hand-computed position (straight drift, wrap) and a headless-browser check that compares the
  frontend's `cloudPositionAt` with the server's level for the same time.
- [Client clock offset ignores latency] → bees and clouds appear up to one latency late; harmless,
  the harvest tolerance (500 ms) already covers it.
- [Floating-point re-anchoring makes server and client differ by ~1e-15] → irrelevant at screen
  resolution.
- [Clicks feel slightly delayed] → one round trip; acceptable, see above.
- [Restart replaces clouds while a flight is under way] → the flight keeps its old path; the bee may
  look slowed by a cloud that is gone for up to ~20 s. Accepted, restarts are rare admin actions.

## Migration Plan

No data migration (in-memory state). Push backend and frontend together at archive time; the
additive contract means an old frontend keeps working against the new backend (without clouds and
with slightly wrong flight animation through clouds) for the minute between the two deploys.
Rollback = revert both commits.
