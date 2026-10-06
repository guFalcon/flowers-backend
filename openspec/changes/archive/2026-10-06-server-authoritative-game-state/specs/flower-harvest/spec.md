## ADDED Requirements

### Requirement: Harvesting at the bee's position yields honey
`POST /api/player/{playerId}/harvest` (no body) SHALL harvest the flower under that player's bee,
judged by the bee's server-side position. If no bee with that id exists, the response SHALL be
`404` and no bee SHALL be created. Otherwise the response SHALL be `200` with the JSON body
`{"flowerId": <id or null>, "gained": <integer>, "total": <integer>}`.

The harvest SHALL succeed only if all of the following hold:
- the bee has arrived at its target, allowing for network latency: the request is received no
  earlier than 500 ms before the bee's computed arrival time; while it succeeds this way the bee's
  position SHALL be taken as its target;
- a flower's centre lies within the flower's harvest radius of the bee's position. The harvest
  radius is `0.175 × size`, measured in play-area heights, with horizontal distances converted from
  play-area widths to heights by the aspect ratio 9:16 (the radius of the flower centre drawn by the
  frontend). If several flowers qualify, the one whose centre is closest SHALL be harvested;
- that flower's fill is above 0.1.

On success `flowerId` SHALL be the harvested flower's id, `gained` SHALL be `round(fill² × 1000)`,
the flower's fill SHALL be set to 0, and `gained` SHALL be added to the bee's honey. Otherwise
`gained` SHALL be 0, `flowerId` SHALL be the id of the flower under the bee or `null` if there is
none, and no flower SHALL change. In both cases `total` SHALL be the bee's honey after the request,
and the bee SHALL be marked as active.

The endpoint `POST /api/harvest/{flowerId}` SHALL no longer exist.

#### Scenario: Harvesting a filled flower after arriving
- **WHEN** bee `p1` has flown to the centre of a flower with fill 0.5, has arrived, and the client posts to `/api/player/p1/harvest`
- **THEN** the response is `200` with that `flowerId`, `gained` 250, and `total` equal to the bee's previous honey plus 250
- **AND** the flower's fill in the next level returned by `GET /api/level/{playerId}` is 0 (or has only grown back by its rate since)

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

## MODIFIED Requirements

### Requirement: Clients show a broadcast harvest immediately
When the frontend receives a `harvest` event, it SHALL set the flower identified by `flowerId` to
the event's `fill` and play the depleted animation, without waiting for the next `level-update`.

When the player's own bee finishes a flight the player started, the frontend SHALL call
`POST /api/player/{playerId}/harvest` once, unless a newer flight has started in the meantime. It
SHALL NOT decide itself which flower is harvested. If `gained` is greater than 0 it SHALL play the
harvest sound and show `total` as the player's honey; otherwise it SHALL play the bump feedback.

#### Scenario: Another player empties a flower
- **WHEN** player A harvests a filled flower while player B is connected
- **THEN** player B's view shows that flower as empty right after the event arrives, before the next `level-update`

#### Scenario: Own flight ends on a flower
- **WHEN** the player clicks a filled flower and the bee arrives there
- **THEN** the frontend posts to `/api/player/{playerId}/harvest`, plays the harvest sound and shows the `total` from the response as honey

#### Scenario: Flight is redirected
- **WHEN** the player clicks a flower and clicks somewhere else before the bee arrives
- **THEN** only the second flight triggers a harvest request

## REMOVED Requirements

### Requirement: Harvesting a flower yields honey and empties it
**Reason**: The client chose the flower by id and the server never checked where the bee was; replaced by a per-player harvest that the server validates against the bee's position.
**Migration**: Call `POST /api/player/{playerId}/harvest` after the bee has arrived; see "Harvesting at the bee's position yields honey".
