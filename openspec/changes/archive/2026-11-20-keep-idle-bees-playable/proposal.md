## Why

On the live server a player's bee disappears after a short idle phase (the backend removes bees after
60 seconds without activity, checked every 10 seconds), and clicking afterwards does nothing until the
page is reloaded: the frontend drops the own bee instance with the level-update and its click handler
then returns early ("No bee instance for yourBeeId yet"), so no request ever reaches the backend to
bring the bee back. In class, players regularly pause longer than a minute, lose their bee and their
honey, and have to reload.

## What Changes

- Backend: raise the inactivity timeout from 60 seconds to 5 minutes.
- Backend: remember every bee removed for inactivity (name, colour, honey, last position) as a
  retired bee. When the same player id joins or steers again, the bee comes back with that name,
  colour, honey and position instead of as a fresh bee. A level restart resets retired bees' honey to
  0 as it does for active bees. If the remembered name is taken by an active bee in the meantime, the
  returning bee gets a newly picked name.
- Frontend: a click while the own bee is not on screen (it was removed) rejoins via
  `GET /api/level/{playerId}` and then sends the click's target, so the bee reappears and flies to
  where the player clicked — no reload needed.
- Frontend: the `setTarget` response is applied to the own bee instance that exists when the
  response arrives, not to the one captured at click time.

No REST/SSE payload shape changes.

## Non-goals

- No heartbeat or "keep alive while the tab is open" mechanism; activity is still only join, steer
  and harvest.
- No persistence across backend restarts; retired bees live in memory like the rest of the state.
- No change to the SSE reconnect logic.
- No eviction policy for retired bees beyond what a classroom produces (one entry per player id).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `game-session`: inactivity timeout becomes 5 minutes; a removed bee is retired and restored on
  rejoin/steer; the frontend rejoins when the player clicks while the own bee is gone.
- `leaderboard`: a bee that returns after an inactivity removal keeps its name (unless now taken)
  instead of getting a newly picked one.

## Impact

- **Backend** (`flowers-backend`): `services/LevelService` (timeout constant, retired-bee map,
  restore in `addBeeIfAbsent`, honey reset on restart), tests in `LevelServiceCleanupTest` and
  related test classes, `http/game.http` comment/request for the rejoin flow.
- **Frontend** (`flowers-frontend`): `harvest.js` (click handler rejoin path, late lookup of the own
  bee instance), possibly `level.js` (`init` reused as rejoin).
- **Deploy/CI**: none.
