## Why

Every flight currently takes the same time per distance, so the only choice a player makes is which
flower to fly to. Clouds drifting over the meadow that slow bees down add a routing decision: players
learn to fly around them or to wait until one has drifted past.

## What Changes

- The level gets 4–6 small drifting clouds (diameter 0.10 play-area heights, a bee is 0.07). Each
  drifts at its own speed roughly in the wind direction and re-enters on the opposite side after
  leaving the meadow.
- One wind direction applies to all clouds. It turns every 15–30 s by up to ±60°, spread over 5–8 s.
  The server publishes a wind schedule reaching at least 60 s into the future, so every cloud's
  position at any time in that window is exactly computable, identically on server and client.
- A bee flying through a cloud flies at 35 % of its normal speed. On `setTarget` the server
  simulates the straight flight against the moving clouds and stores it as a path of keyframes
  `(t, x, y)`; the bee's position, its arrival time and the harvest arrival check follow that path.
- Contract (additive): the level gains `serverTime`, `clouds` and `wind`; every bee gains `path`;
  the `setTarget` response gains the new `path`.
- The frontend no longer computes flight durations itself: it animates every bee along its server
  path (`requestAnimationFrame` instead of the CSS transition), triggers the own harvest at the
  path's arrival time, and draws the clouds from the schedule.
- A restart regenerates the clouds along with the flowers.

## Non-goals

- Clouds have no other effect (no rain, no blocking of harvests, no hiding flowers).
- No pathfinding: bees still fly straight; routing around clouds is the player's job.
- No configuration of cloud or wind parameters at runtime; all numbers are code constants.
- No compensation of network latency beyond the existing 500 ms harvest tolerance and a simple
  server-clock offset in the frontend.

## Capabilities

### New Capabilities
- `clouds`: drifting clouds, the wind schedule, how both are published, how they slow bees, and how
  the frontend draws them.

### Modified Capabilities
- `game-session`: steering now produces a server-computed flight path (and returns it); levels carry
  `serverTime`, `clouds`, `wind` and each bee's `path`; a restart regenerates the clouds; clients
  animate bees along the server path.
- `flower-harvest`: the frontend's own harvest request is triggered at the arrival time of the
  server path instead of a locally computed flight duration.

## Impact

- **Backend** (`flowers-backend`): new DTOs for clouds, wind keyframes and path keyframes;
  `Level` and `Bee` extended; `LevelService` generates clouds, maintains the wind schedule and
  computes flight paths; `GameResource.setTarget` returns the path. New and adapted tests
  (flight, harvest, level contract). `http/game.http` updated.
- **Frontend** (`flowers-frontend`): `bee.js` animates along paths, `bees.js`/`harvest.js` use the
  server paths, new `clouds.js` renders clouds, `state.js` keeps the server-clock offset,
  `styles.css` gets cloud styling.
- **Deploy/CI**: none. Both repos are redeployed by their normal push pipelines; the contract change
  is additive, but the old frontend would ignore clouds, so both are pushed together.
