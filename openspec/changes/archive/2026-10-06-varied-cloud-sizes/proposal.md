## Why

All clouds currently have the same diameter of 0.10 play-area heights, which makes the sky look
uniform and the slowdown predictable. Clouds of different sizes look more natural and make route
choice more interesting: a small cloud costs little time, a big one is worth flying around.

The clouds are also clipped at the edges of the narrow 9:16 play area, while the meadow fills the
whole window. On a landscape screen the clouds visibly vanish at an invisible line next to the
meadow and pop up on the other side of the play area. They should keep drifting across the whole
visible sky.

## What Changes

- Each cloud gets its own random diameter between 0.06 and 0.16 play-area heights, chosen when the
  level is created or restarted (instead of the fixed 0.10).
- Clouds drift over a wider sky: horizontally from −1 to 2 play-area widths (one play-area width on
  each side, enough for 16:9 landscape screens); they wrap around at the edges of this sky instead
  of the play area. Vertically nothing changes.
- The level has three times as many clouds (12 to 18 instead of 4 to 6), spread over the whole sky,
  so the cloud density above the play area stays as it is.
- The frontend no longer clips clouds to the play area; they are drawn wherever the window shows
  them, left and right of the play area as well.
- The REST/SSE shape is unchanged; only the value ranges of `clouds[].x`, `clouds[].size` and the
  number of clouds change. Backend and frontend ship together.

## Non-goals

- Clouds do not grow, shrink or change shape over time.
- No change to cloud speed, drift, wind or the slowdown factor; a big cloud slows exactly as much as
  a small one, just for longer.
- No vertical extension of the sky (the space above and below the play area is only a few pixels on
  landscape screens; on portrait phones the clouds keep wrapping at the play area's top and bottom).
- Bees still fly only within the play area; clouds outside it only matter visually.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `clouds`: "The level has drifting clouds" (random size 0.06–0.16, 12–18 clouds spread over the
  wide sky), "Cloud positions are computable from the published data" (horizontal wrap over −1 to 2
  widths) and "Clients draw the clouds" (not clipped to the play area).

## Impact

- **Backend**: `services/Weather.java` (cloud count, size, initial x range, horizontal wrap),
  tests `WeatherTest`, `GameResourceLevelTest`, flight/harvest tests if they depend on the wrap.
- **Frontend**: `clouds.js` (horizontal wrap range, mirrors the backend), `styles.css`
  (`.cloud-layer` no longer clips).
- **Deploy/CI**: none, but both repos must be pushed together (old frontend with new backend would
  draw the clouds at wrong positions).
- **API**: shape unchanged; `http/game.http` needs no change.
