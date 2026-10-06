## Context

`Weather.java` (backend) and `clouds.js` (frontend) compute cloud positions with the same formula:
anchor + drift along the wind schedule, then `wrap(p, r)` over `[−r, 1 + r]` per axis. The server
uses it for the slowdown of flights, the client for drawing. `.cloud-layer` lives inside
`#playArea` (which has a `transform`, so it forms the stacking context for flowers, bees and clouds)
and has `overflow: hidden`. `body` already has `overflow: hidden`, so anything outside the window is
clipped anyway.

## Goals / Non-Goals

**Goals:**
- One shared, client-independent sky that is wider than the play area, so every client still sees
  the clouds where the server computes them.

**Non-Goals:**
- Adapting the sky to each client's window size.

## Decisions

- **Fixed sky width −1 … 2 play-area widths.** The wrap range for `x` becomes
  `[−1 − r, 2 + r]` (in widths, `r` converted from heights with 9:16); `y` stays `[−r, 1 + r]`.
  A play area is 9:16 of the window height, so ±1 width covers windows up to 27:16 (≈ 16:9 with
  header). *Alternative:* a per-client sky width — rejected, the server must know the wrap range to
  simulate the flight, and positions must be identical on all clients.
- **Generation:** count 12–18 (`MIN_CLOUDS`/`MAX_CLOUDS` ×3), `x` uniform in `[−1, 2]`, `y` uniform
  in `[0, 1]`, size uniform in `[0.06, 0.16]` (`MIN_CLOUD_SIZE`/`MAX_CLOUD_SIZE` replace
  `CLOUD_SIZE`). Constants for the sky range (`SKY_MIN_X = −1`, `SKY_MAX_X = 2`) exist in both
  `Weather.java` and `clouds.js`, with a comment pointing to the counterpart.
- **`wrap(p, min, max, r)`** gets explicit bounds so the same helper serves both axes; the
  `inCloud` check is unchanged (clouds outside the play area simply never contain a bee).
- **Frontend layer stays inside `#playArea`** and only drops `overflow: hidden`; the window clips
  through `body { overflow: hidden }`. This keeps the existing z-order (clouds above bees, below
  the HUD) and the coordinate system (positions relative to the play area). *Alternative:* a
  fixed full-window layer — rejected, it would need its own coordinate offset and would fall out of
  the play area's stacking context.

## Risks / Trade-offs

- [Ultra-wide windows (wider than ~27:16) show an empty strip at the far edges where clouds wrap]
  → accepted; clouds re-enter a whole play-area width away from the meadow.
- [Old cached frontend with new backend draws clouds wrapped over the old range] → both repos
  deploy within minutes; a reload fixes it.
- [3× clouds means 3× work in `inCloud` per simulation step] → at most 18 distance checks per step,
  negligible.
