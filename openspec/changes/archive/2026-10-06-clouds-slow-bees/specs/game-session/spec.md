## MODIFIED Requirements

### Requirement: Steering sets the bee's target
`POST /api/player/{id}/target` with body `{"x": <number>, "y": <number>}` SHALL respond with `200` and
`{"status": "ok", "path": <array>}`, set that bee's `targetX`/`targetY` to the given values and mark
the bee as active. `path` SHALL be the bee's new flight path as described below. A missing
coordinate SHALL be treated as 0. If no bee with that id exists, the backend SHALL create it first
and then set its target.

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
- **WHEN** no bee `p2` exists and the client posts `{"x": 0.5, "y": 0.5}` to `/api/player/p2/target`
- **THEN** a bee `p2` exists afterwards with `targetX` 0.5 and `targetY` 0.5

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
to 11 flowers and the clouds with a newly generated set of 4 to 6 clouds, keep the wind schedule,
keep all current bees with their positions and flights (a flight already under way keeps its path),
reset every bee's `honey` to 0 and publish one SSE event `{"type": "levelRestarted"}`. Without a
valid admin token it SHALL do none of this (see admin-access).

#### Scenario: Admin restarts the level
- **WHEN** bee `p1` exists and a client posts to `/api/admin/restart` with the correct admin token
- **THEN** the response is `200`, every SSE subscriber receives `{"type":"levelRestarted"}`, the level has 6 to 11 freshly generated flowers and 4 to 6 freshly generated clouds, and bee `p1` is still in `bees`

#### Scenario: Restart starts a new round
- **WHEN** bee `p1` has honey 400 and the level is restarted with the correct admin token
- **THEN** bee `p1` has honey 0 in the next level

### Requirement: Level changes are broadcast as level-update
The backend SHALL publish an SSE event `{"type": "level-update", "level": <object>}` on
`GET /api/events`, where `level` is the level as a JSON object (with `aspect`, `serverTime`,
`flowers`, `clouds`, `wind` and `bees`, the same shape as the body of `GET /api/level/{playerId}`
minus a meaningful `yourBeeId`), when a new bee joins, when a bee's target is set, when inactive
bees are removed, and periodically every 3 seconds. `level` SHALL NOT be a JSON-encoded string.
Every client connected to `GET /api/events` SHALL receive each published event as a JSON `data:`
line.

In every level sent to a client (by `GET /api/level/{playerId}` or `level-update`), `serverTime`
SHALL be the server time (epoch milliseconds) at which the level was built, and each bee SHALL carry
`x`/`y` as its current server-side position at that moment, `targetX`/`targetY` as its current
target, `path` as its current flight path, `honey` as its current honey and `name` as its name.

#### Scenario: Setting a target broadcasts the level
- **WHEN** a client is subscribed to `GET /api/events` and bee `p1` sets a target
- **THEN** the client receives a `level-update` event whose `level` is a JSON object containing bee `p1` with the new target and the new path

#### Scenario: Level is sent as an object
- **WHEN** a client is subscribed to `GET /api/events` and a `level-update` is published
- **THEN** the event's `data:` line contains `"level":{` followed by the level's `flowers`, `clouds`, `wind` and `bees` arrays, not a quoted string

#### Scenario: A harvest reaches an HTTP subscriber
- **WHEN** a client holds an open HTTP connection to `GET /api/events` and another client's bee `p2` successfully harvests a flower
- **THEN** the subscriber receives a `data:` line with `{"type":"harvest","flowerId":<id>,"fill":0,"beeId":"p2","honey":<total>}`

#### Scenario: Level shows the bee in flight
- **WHEN** no cloud touches the flight, bee `p1` is half-way on a flight from (0.2, 0.5) to (0.6, 0.5) and a level is built
- **THEN** bee `p1` in that level has `x` 0.4, `y` 0.5, `targetX` 0.6, `targetY` 0.5 and a `path` from (0.2, 0.5) to (0.6, 0.5)

#### Scenario: Level carries the server time
- **WHEN** a level is built at server time T
- **THEN** its `serverTime` is T

#### Scenario: Level carries the honey
- **WHEN** bee `p1` has harvested 250 honey and a level is built
- **THEN** bee `p1` in that level has `honey` 250

#### Scenario: Level carries the name
- **WHEN** bee `p1` is named `Flip` and a level is built
- **THEN** bee `p1` in that level has `name` `Flip`

### Requirement: Clients place bees at their server position
The frontend SHALL keep the offset between the server clock and its own clock, taken from the
`serverTime` of the most recent level it received, and SHALL use it to convert server times
(keyframe and cloud times) to its own time.

The frontend SHALL show every bee at its position on its server path at the current server time,
moving continuously along the path's keyframes, and SHALL NOT compute flight durations itself. When
it first shows a bee (on loading the level or when a bee appears in a `level-update`), it SHALL
place the bee directly at that position without flying in from anywhere. When a level or the own
`setTarget` response brings a new path for a bee already on screen, the bee SHALL continue along the
new path. The flight sound SHALL start and stop with a bee's movement along its path as before.

#### Scenario: Own bee after page load
- **WHEN** the player loads the page and their bee stands still at (0.3, 0.7)
- **THEN** the bee appears at (0.3, 0.7) right away and does not fly in from anywhere

#### Scenario: Another player joins
- **WHEN** a `level-update` contains a new bee standing at (0.8, 0.2)
- **THEN** that bee appears at (0.8, 0.2) without flying

#### Scenario: Reload during a flight
- **WHEN** the player reloads the page while their bee is half-way to its target
- **THEN** the bee appears at the current position from the level and flies on along its path to its target

#### Scenario: Bee slows down in a cloud on screen
- **WHEN** a bee's path passes through a cloud
- **THEN** the bee visibly slows down while it is inside the cloud and speeds up again after leaving it, arriving at the path's last keyframe time
