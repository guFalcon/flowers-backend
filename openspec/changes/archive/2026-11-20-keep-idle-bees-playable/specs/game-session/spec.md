## MODIFIED Requirements

### Requirement: Joining returns the level with the player's own bee
`GET /api/level/{playerId}` SHALL respond with `200` and a JSON level containing `aspect`,
`flowers` (between 6 and 11 flowers), `bees` and `yourBeeId` equal to `playerId`. If no bee with that
id exists and no bee with that id was retired (see "Inactive bees are removed"), the backend SHALL
create one at a random position, standing still (its `targetX`/`targetY` equal to its `x`/`y`), with
`honey` 0 and a random name (see leaderboard), and include it in `bees`. If a bee with that id was
retired, the backend SHALL bring it back as described in "Retired bees come back".
Repeating the request with the same `playerId` SHALL NOT create a second bee, SHALL NOT change its
position, target, honey or name, and SHALL mark the existing bee as active, so the inactivity timeout
starts over as it does after steering.

#### Scenario: New player joins
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists or was retired
- **THEN** the response is `200`, `yourBeeId` is `p1`, `bees` contains exactly one bee with id `p1`, and `flowers` has 6 to 11 entries

#### Scenario: New bee stands still
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists or was retired
- **THEN** bee `p1` in the response has `honey` 0, `targetX` equal to `x` and `targetY` equal to `y`

#### Scenario: Player rejoins
- **WHEN** the same client calls `GET /api/level/p1` a second time
- **THEN** `bees` still contains exactly one bee with id `p1`, with the same colour, name and honey as before

#### Scenario: Rejoining keeps an idle bee alive
- **WHEN** bee `p1` was last active 4 minutes ago, the client calls `GET /api/level/p1`, and the inactive-bee cleanup runs 2 minutes later
- **THEN** bee `p1` is still in the level

### Requirement: Steering sets the bee's target
`POST /api/player/{id}/target` with body `{"x": <number>, "y": <number>}` SHALL respond with `200` and
`{"status": "ok", "path": <array>}`, set that bee's `targetX`/`targetY` to the given values and mark
the bee as active. `path` SHALL be the bee's new flight path as described below. A missing
coordinate SHALL be treated as 0. If no bee with that id exists, the backend SHALL first bring back
the retired bee with that id (see "Retired bees come back") or, if there is none, create a new bee as
on joining, and then set its target.

Setting a target SHALL start a new flight on the server: it starts at the bee's current position at
the moment the request is handled (also when an earlier flight is still under way) and goes in a
straight line to the target. Outside clouds the bee flies at a constant 0.2 units per second
(5 seconds per unit of Euclidean distance in relative coordinates, x and y each from 0 to 1, as sent
by the client); inside a cloud it is slowed (see clouds). A flight SHALL take at least 0.2 seconds.

The server SHALL store the flight as a path: an array of keyframes `{"t": <epoch ms>, "x": <number>,
"y": <number>}` sorted by `t`. The first keyframe SHALL be the start position at the start time, the
last keyframe the target at the arrival time, and between two consecutive keyframes the bee SHALL
move in a straight line at constant speed, so there is a keyframe wherever the speed changes
(entering or leaving a cloud). Before the first keyframe the bee is at the first keyframe's position,
after the last keyframe at the target. A bee that has never been steered has a path with one
keyframe at its position.

#### Scenario: Known player sets a target
- **WHEN** bee `p1` exists and the client posts `{"x": 0.25, "y": 0.75}` to `/api/player/p1/target`
- **THEN** the response is `200` with `status` `ok` and a `path` ending at (0.25, 0.75), and the next level shows bee `p1` with `targetX` 0.25 and `targetY` 0.75

#### Scenario: Unknown player sets a target
- **WHEN** no bee `p2` exists or was retired and the client posts `{"x": 0.5, "y": 0.5}` to `/api/player/p2/target`
- **THEN** a bee `p2` exists afterwards with `targetX` 0.5 and `targetY` 0.5

#### Scenario: Retired player sets a target
- **WHEN** bee `p1` with honey 300 was retired at (0.2, 0.5) and the client posts `{"x": 0.6, "y": 0.5}` to `/api/player/p1/target`
- **THEN** bee `p1` exists afterwards with honey 300, and its path starts at (0.2, 0.5) and ends at (0.6, 0.5)

#### Scenario: Flight duration
- **WHEN** no cloud touches the flight and bee `p1` stands at (0.2, 0.5) and sets the target (0.6, 0.5)
- **THEN** bee `p1` is at (0.4, 0.5) after 1 second and at (0.6, 0.5) from 2 seconds on, and its path has exactly two keyframes, 2 seconds apart

#### Scenario: Redirect mid-flight
- **WHEN** no cloud touches the flights and bee `p1` is half-way on a flight from (0.2, 0.5) to (0.6, 0.5) and sets the target (0.4, 0.9)
- **THEN** the new flight starts at (0.4, 0.5) and takes 2 seconds

#### Scenario: Path through a cloud
- **WHEN** bee `p1` flies straight through a cloud with speed 0, starting and ending outside it
- **THEN** its path has four keyframes: start, entering the cloud, leaving the cloud, and target

### Requirement: Restarting regenerates the flowers and keeps the bees
`POST /api/admin/restart`, when authorised with the admin token, SHALL respond with `200` and
`{"status": "ok", "message": "Level restarted"}`, replace the flowers with a newly generated set of 6
to 11 flowers and the clouds with a newly generated set of 12 to 18 clouds, keep the wind schedule,
keep all current bees with their positions and flights (a flight already under way keeps its path),
reset every bee's `honey` to 0, also the honey of every retired bee, and publish one SSE event
`{"type": "levelRestarted"}`. Without a valid admin token it SHALL do none of this (see
admin-access).

#### Scenario: Admin restarts the level
- **WHEN** bee `p1` exists and a client posts to `/api/admin/restart` with the correct admin token
- **THEN** the response is `200`, every SSE subscriber receives `{"type":"levelRestarted"}`, the level has 6 to 11 freshly generated flowers and 12 to 18 freshly generated clouds, and bee `p1` is still in `bees`

#### Scenario: Restart starts a new round
- **WHEN** bee `p1` has honey 400 and the level is restarted with the correct admin token
- **THEN** bee `p1` has honey 0 in the next level

#### Scenario: Restart also resets retired bees
- **WHEN** bee `p1` was retired with honey 400, the level is restarted with the correct admin token, and `p1` joins again
- **THEN** bee `p1` is back with honey 0

### Requirement: Inactive bees are removed
The backend SHALL periodically remove every bee that has not been active for more than 5 minutes and,
if at least one bee was removed, publish a `level-update` event without those bees. Bees active within
the last 5 minutes SHALL be kept. Every removed bee SHALL be retired: the backend SHALL remember its
id, name, colour, honey and its position at the moment of removal until that id joins or steers
again.

#### Scenario: Idle bee times out
- **WHEN** bee `p1` was last active more than 5 minutes ago, bee `p2` was active just now, and the cleanup runs
- **THEN** `p1` is no longer in the level, `p2` still is, and a `level-update` event is published

#### Scenario: Bee idle for a few minutes stays
- **WHEN** bee `p1` was last active 4 minutes ago and the cleanup runs
- **THEN** `p1` is still in the level

## ADDED Requirements

### Requirement: Retired bees come back
When a player id whose bee was retired joins (`GET /api/level/{playerId}`) or steers
(`POST /api/player/{id}/target`), the backend SHALL bring the bee back instead of creating a new one:
with the same id, colour and honey as at retirement, standing still at its position at retirement
(`targetX`/`targetY` equal to `x`/`y`, a path with one keyframe there), marked as active, and with
its name as described in leaderboard. The bee SHALL then no longer be retired. Bringing a bee back
SHALL publish a `level-update` as joining does.

#### Scenario: Retired bee rejoins
- **WHEN** bee `p1` named `Willi` with colour C and honey 250 stood at (0.3, 0.7) when it was retired, and the client calls `GET /api/level/p1`
- **THEN** `bees` contains exactly one bee `p1` with name `Willi`, colour C, honey 250, `x` 0.3, `y` 0.7, `targetX` 0.3 and `targetY` 0.7

#### Scenario: Returned bee times out again
- **WHEN** retired bee `p1` came back, stays idle for more than 5 minutes and the cleanup runs
- **THEN** `p1` is removed and retired again with its current honey

### Requirement: Clients rejoin when their bee is gone
When the player clicks the play area while the own bee is not on screen (for example because it was
removed for inactivity), the frontend SHALL rejoin by loading the level with `GET
/api/level/{playerId}` and showing it, and SHALL then send the click's target as a normal steering
request, so the own bee reappears and flies to the clicked point without a page reload. The frontend
SHALL apply a `setTarget` response to the own bee that is on screen when the response arrives.

#### Scenario: Click after the bee was removed
- **WHEN** the player's bee was removed for inactivity, the page was not reloaded, and the player clicks at (0.6, 0.4)
- **THEN** the own bee appears again (with its previous name and honey) and flies to (0.6, 0.4)

#### Scenario: Click with the bee on screen
- **WHEN** the own bee is on screen and the player clicks
- **THEN** the frontend sends the steering request directly without loading the level first
