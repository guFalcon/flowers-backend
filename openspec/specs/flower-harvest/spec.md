# flower-harvest Specification

## Purpose

Covers how a player harvests honey from a flower via the REST API, and how every connected client
learns over SSE that the flower has been emptied.

## Requirements

### Requirement: Harvesting at the bee's position yields honey
`POST /api/player/{playerId}/harvest` (no body) SHALL harvest the flower under that player's bee,
judged by the bee's server-side position. If no bee with that id exists, the response SHALL be
`404` and no bee SHALL be created. Otherwise the response SHALL be `200` with the JSON body
`{"flowerId": <id or null>, "gained": <integer>, "total": <integer>}`.

Honey SHALL be measured in microlitres (µl) everywhere it appears in the API: `gained` and `total`
of the harvest response, `honey` of a bee in a level and `honey` of the `harvest` event are integer
microlitres.

The harvest SHALL succeed only if all of the following hold:
- the bee has arrived at its target, allowing for network latency: the request is received no
  earlier than 500 ms before the bee's computed arrival time; while it succeeds this way the bee's
  position SHALL be taken as its target;
- a flower's centre lies within the flower's harvest radius of the bee's position. The harvest
  radius is `0.175 × size`, measured in play-area heights, with horizontal distances converted from
  play-area widths to heights by the aspect ratio 9:16 (the radius of the flower centre drawn by the
  frontend). If several flowers qualify, the one whose centre is closest SHALL be harvested;
- that flower's fill is above 0.1.

On success `flowerId` SHALL be the harvested flower's id, `gained` SHALL be `round(fill² × 50)`
(a full flower yields 50 µl; halves round up), the flower's fill SHALL be set to 0, and `gained`
SHALL be added to the bee's honey. Otherwise `gained` SHALL be 0, `flowerId` SHALL be the id of the
flower under the bee or `null` if there is none, and no flower SHALL change. In both cases `total`
SHALL be the bee's honey after the request, and the bee SHALL be marked as active.

The endpoint `POST /api/harvest/{flowerId}` SHALL no longer exist.

#### Scenario: Harvesting a filled flower after arriving
- **WHEN** bee `p1` has flown to the centre of a flower with fill 0.5, has arrived, and the client posts to `/api/player/p1/harvest`
- **THEN** the response is `200` with that `flowerId`, `gained` 13, and `total` equal to the bee's previous honey plus 13
- **AND** the flower's fill in the next level returned by `GET /api/level/{playerId}` is 0 (or has only grown back by its rate since)

#### Scenario: Harvesting a full flower
- **WHEN** bee `p1` has arrived on a flower with fill 1.0 and the client posts to `/api/player/p1/harvest`
- **THEN** the response has `gained` 50

#### Scenario: Harvesting while still in flight
- **WHEN** bee `p1` is flying to a filled flower, its arrival is more than 500 ms away, and the client posts to `/api/player/p1/harvest`
- **THEN** the response is `200` with `gained` 0 and the flower's fill is unchanged

#### Scenario: Harvesting slightly early because of latency
- **WHEN** bee `p1` is flying to the centre of a filled flower, its arrival is 200 ms away, and the client posts to `/api/player/p1/harvest`
- **THEN** the harvest succeeds as if the bee had arrived

#### Scenario: Bee is not on a flower
- **WHEN** bee `p1` has arrived at a point that is outside every flower's harvest radius and the client posts to `/api/player/p1/harvest`
- **THEN** the response is `200` with `flowerId` null, `gained` 0, and no flower changes

#### Scenario: Harvesting an almost empty flower
- **WHEN** bee `p1` has arrived on a flower whose fill is 0.1 or less and the client posts to `/api/player/p1/harvest`
- **THEN** the response is `200` with `gained` 0 and the flower's fill is unchanged

#### Scenario: Unknown player
- **WHEN** no bee `ghost` exists and a client posts to `/api/player/ghost/harvest`
- **THEN** the response is `404` and no bee `ghost` exists afterwards

#### Scenario: Old endpoint is gone
- **WHEN** a client posts to `/api/harvest/flower-0`
- **THEN** the response is `404` and no flower changes

### Requirement: A successful harvest is broadcast to all clients
After a successful harvest (honey greater than 0), the backend SHALL publish exactly one SSE event
`{"type": "harvest", "flowerId": <id>, "fill": 0, "beeId": <id>, "honey": <integer>}` on
`GET /api/events` to every connected client, where `beeId` is the id of the harvesting bee and
`honey` its honey in µl after the harvest (equal to `total` of the harvest response). A harvest that
yields no honey SHALL NOT publish a `harvest` event.

#### Scenario: Successful harvest publishes an event
- **WHEN** bee `p1` with 100 honey harvests a filled flower and gains 13
- **THEN** every SSE subscriber receives `{"type":"harvest","flowerId":<id>,"fill":0,"beeId":"p1","honey":113}`

#### Scenario: Failed harvest publishes nothing
- **WHEN** a harvest yields 0 honey (empty or unknown flower)
- **THEN** no `harvest` event is published

### Requirement: Clients show a broadcast harvest immediately
When the frontend receives a `harvest` event, it SHALL set the flower identified by `flowerId` to
the event's `fill` and play the depleted animation, without waiting for the next `level-update`.

When the player's own bee finishes a flight the player started, the frontend SHALL call
`POST /api/player/{playerId}/harvest` once, unless a newer flight has started in the meantime. The
flight is finished at the time of the last keyframe of the path returned by the `setTarget`
response, converted to the client's clock with the server-clock offset (see game-session); the
frontend SHALL NOT compute the flight duration itself. It SHALL NOT decide itself which flower is
harvested. On the response it SHALL:
- if `gained` is greater than 0, play the harvest sound (the own honey shown in the leaderboard is
  updated by the `harvest` event);
- if `gained` is 0 and `flowerId` is not null (the bee is on a flower that yields nothing), play the
  bump feedback;
- if `flowerId` is null (the bee is not on any flower), play no sound and give no bump feedback.

If the harvest request fails (no `200` response), the frontend SHALL give no harvest or bump
feedback. If the `setTarget` request fails, it SHALL NOT send a harvest request for that flight.

#### Scenario: Another player empties a flower
- **WHEN** player A harvests a filled flower while player B is connected
- **THEN** player B's view shows that flower as empty right after the event arrives, before the next `level-update`

#### Scenario: Own flight ends on a flower
- **WHEN** the player clicks a filled flower and the bee arrives there
- **THEN** the frontend posts to `/api/player/{playerId}/harvest` and plays the harvest sound, and the own row in the leaderboard shows the new honey once the `harvest` event arrives

#### Scenario: Own flight through a cloud
- **WHEN** the player clicks a filled flower behind a cloud and the server path is slowed by the cloud
- **THEN** the frontend posts the harvest request only when the bee reaches the flower at the path's arrival time, and the harvest succeeds

#### Scenario: Own flight ends on an empty flower
- **WHEN** the player's bee arrives on a flower that another player has just emptied, and the harvest response has that `flowerId` and `gained` 0
- **THEN** the frontend plays the bump feedback and the own honey in the leaderboard stays the same

#### Scenario: Own flight ends on the bare meadow
- **WHEN** the player clicks a spot outside every flower and the bee arrives there, and the harvest response has `flowerId` null
- **THEN** the frontend plays neither the harvest sound nor the bump feedback

#### Scenario: Flight is redirected
- **WHEN** the player clicks a flower and clicks somewhere else before the bee arrives
- **THEN** only the second flight triggers a harvest request

#### Scenario: Another player joins
- **WHEN** a `level-update` contains a new bee standing at (0.8, 0.2)
- **THEN** that bee appears at (0.8, 0.2) without flying

#### Scenario: Reload during a flight
- **WHEN** the player reloads the page while their bee is half-way to its target
- **THEN** the bee appears at the current position from the level and flies on to its target
