## Why

Since server-authoritative-game-state the frontend asks the server to harvest after every own
flight and plays the bump whenever nothing was gained — so every stop on the bare meadow now bumps.
The bump is meant to tell the player one thing only: "you landed on a flower, but it was empty"
(typically another player got there first).

## What Changes

- The frontend plays the bump (and the short vibration) only when the harvest response names a
  flower (`flowerId` not null) and `gained` is 0.
- A flight that ends off every flower (`flowerId` null) gives no sound and no vibration.
- A harvest request that fails (non-2xx, network error) gives no feedback either.
- Frontend README and the backend's game-round diagram describe the new feedback rule.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `flower-harvest`: the requirement "Clients show a broadcast harvest immediately" narrows when the
  bump feedback is played.

## Non-goals

- No change to the REST/SSE contract or to the backend's harvest logic — the response already
  carries `flowerId`.
- No new sound for landing on the meadow, no change to the slurp feedback.
- No change to when the frontend sends the harvest request (still after every own flight).

## Impact

- **Frontend** (`flowers-frontend`): `harvest.js` feedback branch, `README.md`.
- **Backend** (`flowers-backend`): no code change; `docs/diagrams/game-round.puml` + rendered SVG.
- **Deploy/CI**: none beyond the usual redeploy on push.
