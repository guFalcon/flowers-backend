# clouds Specification

## Purpose

Covers the small clouds that drift over the meadow with the wind: how the server generates them and
the wind, how both are published so every client computes the same cloud positions, how a cloud
slows a bee flying through it, and how the frontend draws the clouds.

## Requirements

### Requirement: The level has drifting clouds
Every level SHALL contain between 4 and 6 clouds. Each cloud SHALL have a diameter of 0.10
play-area heights, its own drift speed between 0.02 and 0.05 play-area heights per second, and its
own fixed drift offset of at most ±20° from the wind direction. Clouds SHALL be placed at random
positions when the level is created or restarted.

Coordinates follow the level's convention: `x` is relative to the play-area width, `y` relative to
the play-area height, (0, 0) is the top-left corner. Distances "in play-area heights" convert
horizontal differences from widths to heights with the aspect ratio 9:16.

#### Scenario: New level has clouds
- **WHEN** a client calls `GET /api/level/p1`
- **THEN** the level's `clouds` has 4 to 6 entries, each with `size` 0.10, a `speed` between 0.02 and 0.05, and a `drift` between −20° and +20° (in radians)

#### Scenario: Restart creates new clouds
- **WHEN** the level is restarted with the correct admin token
- **THEN** the next level has 4 to 6 clouds with newly chosen positions, speeds and drifts

### Requirement: One wind drives all clouds
The server SHALL keep one wind direction for all clouds. The wind SHALL change direction every 15 to
30 seconds by a random angle of at most ±60°, spread evenly over a turn of 5 to 8 seconds in steps of
at most 0.5 seconds, and SHALL hold its direction between turns. Every level the server returns or
publishes SHALL contain the wind schedule from at most the level's `serverTime` until at least 60
seconds after it.

#### Scenario: Schedule reaches into the future
- **WHEN** a level is built at server time T
- **THEN** its `wind` starts at or before T and its last entry lies at least 60 seconds after T

#### Scenario: Gradual turns
- **WHEN** the wind turns
- **THEN** the angle changes in equal steps at most 0.5 seconds apart, the turn takes 5 to 8 seconds in total and changes the direction by at most 60°, and the next turn starts 15 to 30 seconds after this one started

### Requirement: Cloud positions are computable from the published data
The level SHALL carry:
- `serverTime`: the server time (epoch milliseconds) at which the level was built;
- `wind`: an array of `{"t": <epoch ms>, "angle": <radians>}` sorted by `t`, where `angle` 0 points
  towards growing `x` and π/2 towards growing `y`; an entry's angle holds from its `t` until the
  next entry's `t`, the last entry's angle holds after it;
- `clouds`: an array of `{"id", "x", "y", "t", "size", "speed", "drift"}`, where (`x`, `y`) is the
  cloud's position at time `t` (epoch ms, never earlier than the first `wind` entry), `size` its
  diameter in play-area heights, `speed` its drift speed in play-area heights per second and `drift`
  its offset to the wind in radians.

A cloud's position at any time T ≥ `t` covered by the schedule SHALL be its position at `t` moved,
piece by piece along the wind schedule, by `speed` in direction `wind angle + drift` (horizontal
movement converted from heights to widths by 9:16), and then wrapped: a cloud that has completely
left the play area on one side re-enters on the opposite side, i.e. `x` is taken modulo the range
from `−r` to `1 + r` in widths and `y` modulo the range from `−r` to `1 + r` in heights, where `r`
is the cloud's radius. The server SHALL use exactly this computation for the flight simulation, so
a client computing it from the published data sees the clouds where the server sees them.

#### Scenario: Straight drift
- **WHEN** a cloud is at (0.5, 0.5) at time t with speed 0.04, drift 0, and the wind angle is π/2 for the next 10 seconds
- **THEN** the cloud is at (0.5, 0.9) 10 seconds later

#### Scenario: Wrap around
- **WHEN** a cloud drifts downwards and its `y` passes 1.05
- **THEN** it continues from `y` −0.05 at the same `x`

#### Scenario: Same position on server and client
- **WHEN** the frontend computes a cloud's position from the latest level for some time within the schedule
- **THEN** the result equals the position the server uses for that cloud at that time

### Requirement: Clouds slow bees down
While a bee's centre lies within a cloud (its distance to the cloud's centre, in play-area heights,
is at most the cloud's radius), the bee SHALL fly at 35 % of its normal speed. Outside clouds it
SHALL fly at its normal speed of 0.2 units per second (5 seconds per unit of distance in relative
coordinates). Overlapping clouds SHALL NOT slow the bee further. The server SHALL account for the
clouds' movement during the whole flight when computing it, and its computed arrival time SHALL be
within 0.1 seconds of the exact arrival time.

#### Scenario: Flight through a resting cloud
- **WHEN** a cloud with speed 0 lies at (0.4, 0.5) and bee `p1` flies from (0.2, 0.5) to (0.6, 0.5)
- **THEN** the flight takes about 3.65 seconds (±0.1 s) instead of 2 seconds, and the bee is slowed only while it is between x ≈ 0.311 and x ≈ 0.489

#### Scenario: Flight past a cloud
- **WHEN** no cloud touches the straight line from (0.2, 0.5) to (0.6, 0.5) during the flight
- **THEN** the flight takes 2 seconds

#### Scenario: A drifting cloud catches the bee
- **WHEN** a cloud drifts across the bee's straight line and reaches it while the bee is passing
- **THEN** the bee is slowed for as long as its centre is inside the moving cloud

#### Scenario: Harvest waits for the slowed arrival
- **WHEN** bee `p1` flies through a cloud to a filled flower, and the client posts to `/api/player/p1/harvest` at the time the flight would have arrived without the cloud (more than 500 ms before the slowed arrival)
- **THEN** the response has `gained` 0 and the flower's fill is unchanged

### Requirement: Clients draw the clouds
The frontend SHALL draw every cloud of the latest level as a soft, semi-transparent cloud of the
cloud's `size` (in play-area heights), above the flowers and bees, without catching clicks. It SHALL
move each cloud continuously to its position at the current server time, computed from the level's
data as specified above, and SHALL replace its clouds with those of every new level.

#### Scenario: Clouds drift on screen
- **WHEN** a player watches the meadow for a few seconds without any event arriving
- **THEN** the clouds move smoothly in the wind direction and a cloud leaving one edge comes back at the opposite edge

#### Scenario: Clicking through a cloud
- **WHEN** the player clicks a spot covered by a cloud
- **THEN** the bee flies to that spot as with any other click
