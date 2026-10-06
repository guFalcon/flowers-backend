# game-session Specification

## Purpose

Covers how a player joins the game and steers their bee over the REST API, how the level is
restarted and idle bees are removed, how every client is kept in sync over SSE, and that the shared
game state stays consistent while many requests and background jobs run at the same time.

## Requirements

### Requirement: Joining returns the level with the player's own bee
`GET /api/level/{playerId}` SHALL respond with `200` and a JSON level containing `aspect`,
`flowers` (between 6 and 11 flowers), `bees` and `yourBeeId` equal to `playerId`. If no bee with that
id exists, the backend SHALL create one and include it in `bees`. Repeating the request with the same
`playerId` SHALL NOT create a second bee.

#### Scenario: New player joins
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists
- **THEN** the response is `200`, `yourBeeId` is `p1`, `bees` contains exactly one bee with id `p1`, and `flowers` has 6 to 11 entries

#### Scenario: Player rejoins
- **WHEN** the same client calls `GET /api/level/p1` a second time
- **THEN** `bees` still contains exactly one bee with id `p1`, with the same colour as before

### Requirement: Steering sets the bee's target
`POST /api/player/{id}/target` with body `{"x": <number>, "y": <number>}` SHALL respond with `200` and
`{"status": "ok"}`, set that bee's `targetX`/`targetY` to the given values and mark the bee as active.
A missing coordinate SHALL be treated as 0. If no bee with that id exists, the backend SHALL create it
first and then set its target.

#### Scenario: Known player sets a target
- **WHEN** bee `p1` exists and the client posts `{"x": 0.25, "y": 0.75}` to `/api/player/p1/target`
- **THEN** the response is `200` with `{"status":"ok"}` and the next level shows bee `p1` with `targetX` 0.25 and `targetY` 0.75

#### Scenario: Unknown player sets a target
- **WHEN** no bee `p2` exists and the client posts `{"x": 0.5, "y": 0.5}` to `/api/player/p2/target`
- **THEN** a bee `p2` exists afterwards with `targetX` 0.5 and `targetY` 0.5

### Requirement: Restarting regenerates the flowers and keeps the bees
`POST /api/admin/restart` SHALL respond with `200` and
`{"status": "ok", "message": "Level restarted"}`, replace the flowers with a newly generated set of 6
to 11 flowers, keep all current bees and publish one SSE event `{"type": "levelRestarted"}`.

#### Scenario: Admin restarts the level
- **WHEN** bee `p1` exists and a client posts to `/api/admin/restart`
- **THEN** the response is `200`, every SSE subscriber receives `{"type":"levelRestarted"}`, the level has 6 to 11 freshly generated flowers, and bee `p1` is still in `bees`

### Requirement: Inactive bees are removed
The backend SHALL periodically remove every bee that has not been active for more than 60 seconds and,
if at least one bee was removed, publish a `level-update` event without those bees. Bees active within
the last 60 seconds SHALL be kept.

#### Scenario: Idle bee times out
- **WHEN** bee `p1` was last active more than 60 seconds ago, bee `p2` was active just now, and the cleanup runs
- **THEN** `p1` is no longer in the level, `p2` still is, and a `level-update` event is published

### Requirement: Level changes are broadcast as level-update
The backend SHALL publish an SSE event `{"type": "level-update", "level": <object>}` on
`GET /api/events`, where `level` is the level as a JSON object (with `aspect`, `flowers` and
`bees`, the same shape as the body of `GET /api/level/{playerId}` minus a meaningful `yourBeeId`),
when a new bee joins, when a bee's target is set, when inactive bees are removed, and periodically
every 3 seconds. `level` SHALL NOT be a JSON-encoded string. Every client connected to
`GET /api/events` SHALL receive each published event as a JSON `data:` line.

#### Scenario: Setting a target broadcasts the level
- **WHEN** a client is subscribed to `GET /api/events` and bee `p1` sets a target
- **THEN** the client receives a `level-update` event whose `level` is a JSON object containing bee `p1` with the new target

#### Scenario: Level is sent as an object
- **WHEN** a client is subscribed to `GET /api/events` and a `level-update` is published
- **THEN** the event's `data:` line contains `"level":{` followed by the level's `flowers` and `bees` arrays, not a quoted string

#### Scenario: A harvest reaches an HTTP subscriber
- **WHEN** a client holds an open HTTP connection to `GET /api/events` and another client successfully harvests a flower
- **THEN** the subscriber receives a `data:` line with `{"type":"harvest","flowerId":<id>,"fill":0}`

### Requirement: Clients apply level-updates to existing flowers
When the frontend receives a `level-update`, it SHALL match the level's flowers to the flowers on
screen by `id`. A flower whose position, size, petal count and colours are unchanged SHALL keep its
on-screen element and only take over the new `fill` and `rate`. A flower whose position, size,
petal count or colours changed SHALL be redrawn. Flowers new in the level SHALL be added, and
flowers no longer in the level SHALL be removed.

#### Scenario: Periodic update keeps the flower elements
- **WHEN** a client shows the level and receives a `level-update` with the same flowers but different fills
- **THEN** every flower keeps its on-screen element and shows the fill from the update

#### Scenario: Restarted level replaces changed flowers
- **WHEN** the level was restarted and a `level-update` arrives whose flower `flower-0` has a different position and colours than the one on screen
- **THEN** the client redraws `flower-0` at its new position with its new colours

#### Scenario: Flower count changes
- **WHEN** a `level-update` contains fewer or more flowers than are on screen
- **THEN** flowers missing from the update disappear and new flowers appear, so the screen shows exactly the flowers of the update

### Requirement: Clients predict flower growth at the server's pace
Between `level-update` events the frontend SHALL grow each flower's displayed fill by its `rate`
once per second, capped at 1, matching the server's growth step. The fill received in a
`level-update` or `harvest` event SHALL replace the predicted value.

#### Scenario: Fill grows between updates
- **WHEN** a flower with fill 0.2 and rate 0.05 is shown and no event arrives for 2 seconds
- **THEN** the flower shows a fill of about 0.3

#### Scenario: Server value wins
- **WHEN** the client predicted a fill of 0.4 and a `level-update` reports a fill of 0.35 for that flower
- **THEN** the flower shows 0.35 and continues growing from there

### Requirement: Game state stays consistent under concurrent access
Concurrent requests (join, steer, harvest, restart) and the periodic background jobs (flower growth,
inactive-bee cleanup, level broadcast) SHALL NOT fail with an error caused by concurrent modification,
and SHALL NOT lose updates: a flower emptied by a successful harvest SHALL have a fill of 0 right after
the harvest, with growth applied only afterwards, and a level returned to a client SHALL NOT change
while it is being serialised.

#### Scenario: Many players and background jobs at once
- **WHEN** many threads join, steer and harvest in parallel while flower growth, cleanup and broadcast run repeatedly
- **THEN** every request responds successfully, no background job throws, and every bee that joined and stayed active is present in the level afterwards

#### Scenario: Harvest and growth do not interleave
- **WHEN** a harvest of a filled flower and a flower-growth step happen at the same time
- **THEN** the flower's fill afterwards is either 0 (growth before harvest) or exactly its rate (growth after harvest), never its pre-harvest fill plus rate
