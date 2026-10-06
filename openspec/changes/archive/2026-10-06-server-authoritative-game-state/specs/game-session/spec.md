## MODIFIED Requirements

### Requirement: Joining returns the level with the player's own bee
`GET /api/level/{playerId}` SHALL respond with `200` and a JSON level containing `aspect`,
`flowers` (between 6 and 11 flowers), `bees` and `yourBeeId` equal to `playerId`. If no bee with that
id exists, the backend SHALL create one at a random position, standing still (its `targetX`/`targetY`
equal to its `x`/`y`), with `honey` 0, and include it in `bees`. Repeating the request with the same
`playerId` SHALL NOT create a second bee, SHALL NOT change its position, target or honey, and SHALL
mark the existing bee as active, so the inactivity timeout starts over as it does after steering.

#### Scenario: New player joins
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists
- **THEN** the response is `200`, `yourBeeId` is `p1`, `bees` contains exactly one bee with id `p1`, and `flowers` has 6 to 11 entries

#### Scenario: New bee stands still
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists
- **THEN** bee `p1` in the response has `honey` 0, `targetX` equal to `x` and `targetY` equal to `y`

#### Scenario: Player rejoins
- **WHEN** the same client calls `GET /api/level/p1` a second time
- **THEN** `bees` still contains exactly one bee with id `p1`, with the same colour and honey as before

#### Scenario: Rejoining keeps an idle bee alive
- **WHEN** bee `p1` was last active 50 seconds ago, the client calls `GET /api/level/p1`, and the inactive-bee cleanup runs 20 seconds later
- **THEN** bee `p1` is still in the level

### Requirement: Steering sets the bee's target
`POST /api/player/{id}/target` with body `{"x": <number>, "y": <number>}` SHALL respond with `200` and
`{"status": "ok"}`, set that bee's `targetX`/`targetY` to the given values and mark the bee as active.
A missing coordinate SHALL be treated as 0. If no bee with that id exists, the backend SHALL create it
first and then set its target.

Setting a target SHALL start a new flight on the server: it starts at the bee's current position at
the moment the request is handled (also when an earlier flight is still under way), goes in a
straight line at constant speed to the target, and takes `max(5 s × d, 0.2 s)`, where `d` is the
Euclidean distance between start and target in relative coordinates (x and y each from 0 to 1, as
sent by the client). This is the same duration the frontend uses to animate the flight.

#### Scenario: Known player sets a target
- **WHEN** bee `p1` exists and the client posts `{"x": 0.25, "y": 0.75}` to `/api/player/p1/target`
- **THEN** the response is `200` with `{"status":"ok"}` and the next level shows bee `p1` with `targetX` 0.25 and `targetY` 0.75

#### Scenario: Unknown player sets a target
- **WHEN** no bee `p2` exists and the client posts `{"x": 0.5, "y": 0.5}` to `/api/player/p2/target`
- **THEN** a bee `p2` exists afterwards with `targetX` 0.5 and `targetY` 0.5

#### Scenario: Flight duration
- **WHEN** bee `p1` stands at (0.2, 0.5) and sets the target (0.6, 0.5)
- **THEN** bee `p1` is at (0.4, 0.5) after 1 second and at (0.6, 0.5) from 2 seconds on

#### Scenario: Redirect mid-flight
- **WHEN** bee `p1` is half-way on a flight from (0.2, 0.5) to (0.6, 0.5) and sets the target (0.4, 0.9)
- **THEN** the new flight starts at (0.4, 0.5) and takes 2 seconds

### Requirement: Restarting regenerates the flowers and keeps the bees
`POST /api/admin/restart`, when authorised with the admin token, SHALL respond with `200` and
`{"status": "ok", "message": "Level restarted"}`, replace the flowers with a newly generated set of 6
to 11 flowers, keep all current bees with their positions and flights, reset every bee's `honey` to
0 and publish one SSE event `{"type": "levelRestarted"}`. Without a valid admin token it SHALL do
none of this (see admin-access).

#### Scenario: Admin restarts the level
- **WHEN** bee `p1` exists and a client posts to `/api/admin/restart` with the correct admin token
- **THEN** the response is `200`, every SSE subscriber receives `{"type":"levelRestarted"}`, the level has 6 to 11 freshly generated flowers, and bee `p1` is still in `bees`

#### Scenario: Restart starts a new round
- **WHEN** bee `p1` has honey 400 and the level is restarted with the correct admin token
- **THEN** bee `p1` has honey 0 in the next level

### Requirement: Level changes are broadcast as level-update
The backend SHALL publish an SSE event `{"type": "level-update", "level": <object>}` on
`GET /api/events`, where `level` is the level as a JSON object (with `aspect`, `flowers` and
`bees`, the same shape as the body of `GET /api/level/{playerId}` minus a meaningful `yourBeeId`),
when a new bee joins, when a bee's target is set, when inactive bees are removed, and periodically
every 3 seconds. `level` SHALL NOT be a JSON-encoded string. Every client connected to
`GET /api/events` SHALL receive each published event as a JSON `data:` line.

In every level sent to a client (by `GET /api/level/{playerId}` or `level-update`), each bee SHALL
carry `x`/`y` as its current server-side position at the moment the level was built,
`targetX`/`targetY` as its current target, and `honey` as its current honey.

#### Scenario: Setting a target broadcasts the level
- **WHEN** a client is subscribed to `GET /api/events` and bee `p1` sets a target
- **THEN** the client receives a `level-update` event whose `level` is a JSON object containing bee `p1` with the new target

#### Scenario: Level is sent as an object
- **WHEN** a client is subscribed to `GET /api/events` and a `level-update` is published
- **THEN** the event's `data:` line contains `"level":{` followed by the level's `flowers` and `bees` arrays, not a quoted string

#### Scenario: A harvest reaches an HTTP subscriber
- **WHEN** a client holds an open HTTP connection to `GET /api/events` and another client successfully harvests a flower
- **THEN** the subscriber receives a `data:` line with `{"type":"harvest","flowerId":<id>,"fill":0}`

#### Scenario: Level shows the bee in flight
- **WHEN** bee `p1` is half-way on a flight from (0.2, 0.5) to (0.6, 0.5) and a level is built
- **THEN** bee `p1` in that level has `x` 0.4, `y` 0.5, `targetX` 0.6 and `targetY` 0.5

#### Scenario: Level carries the honey
- **WHEN** bee `p1` has harvested 250 honey and a level is built
- **THEN** bee `p1` in that level has `honey` 250

## ADDED Requirements

### Requirement: Clients place bees at their server position
When the frontend first shows a bee (on loading the level or when a bee appears in a
`level-update`), it SHALL place the bee directly at the level's `x`/`y` without a flight animation.
If that bee's target differs from its position, it SHALL then fly from there to the target. A bee
already on screen SHALL keep flying to a changed target as before.

#### Scenario: Own bee after page load
- **WHEN** the player loads the page and their bee stands still at (0.3, 0.7)
- **THEN** the bee appears at (0.3, 0.7) right away and does not fly in from anywhere

#### Scenario: Another player joins
- **WHEN** a `level-update` contains a new bee standing at (0.8, 0.2)
- **THEN** that bee appears at (0.8, 0.2) without flying

#### Scenario: Reload during a flight
- **WHEN** the player reloads the page while their bee is half-way to its target
- **THEN** the bee appears at the current position from the level and flies on to its target

### Requirement: Clients show the player's honey from the server
The frontend SHALL show as the player's honey the `honey` of the player's own bee from the most
recent level (from `GET /api/level/{playerId}` or `level-update`) or the `total` of the most recent
harvest response, whichever arrived last. It SHALL NOT keep a separate client-side score.

#### Scenario: Honey survives a reload
- **WHEN** the player has 400 honey and reloads the page within the inactivity timeout
- **THEN** the page shows 400 honey after loading

#### Scenario: Restart resets the display
- **WHEN** the admin restarts the level and the client reloads the level
- **THEN** the page shows 0 honey
