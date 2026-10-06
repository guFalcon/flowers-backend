## MODIFIED Requirements

### Requirement: The level has drifting clouds
Every level SHALL contain between 12 and 18 clouds. Each cloud SHALL have its own diameter between
0.06 and 0.16 play-area heights, its own drift speed between 0.02 and 0.05 play-area heights per
second, and its own fixed drift offset of at most ±20° from the wind direction. Diameters, speeds
and drifts SHALL be chosen at random, independently per cloud. Clouds SHALL be placed at random
positions in the sky when the level is created or restarted: `x` between −1 and 2, `y` between 0
and 1.

The sky extends one play-area width to the left and to the right of the play area, so clouds can be
seen drifting next to the play area on wide screens. Coordinates follow the level's convention:
`x` is relative to the play-area width, `y` relative to the play-area height, (0, 0) is the
top-left corner of the play area. Distances "in play-area heights" convert horizontal differences
from widths to heights with the aspect ratio 9:16.

#### Scenario: New level has clouds
- **WHEN** a client calls `GET /api/level/p1`
- **THEN** the level's `clouds` has 12 to 18 entries, each with an `x` between −1 and 2, a `y` between 0 and 1, a `size` between 0.06 and 0.16, a `speed` between 0.02 and 0.05, and a `drift` between −20° and +20° (in radians)

#### Scenario: Clouds differ in size
- **WHEN** several levels are generated
- **THEN** their clouds do not all have the same `size`

#### Scenario: Restart creates new clouds
- **WHEN** the level is restarted with the correct admin token
- **THEN** the next level has 12 to 18 clouds with newly chosen positions, sizes, speeds and drifts

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
left the sky on one side re-enters on the opposite side, i.e. `x` is taken modulo the range from
`−1 − r` to `2 + r` in widths and `y` modulo the range from `−r` to `1 + r` in heights, where `r`
is the cloud's radius. The server SHALL use exactly this computation for the flight simulation, so
a client computing it from the published data sees the clouds where the server sees them.

#### Scenario: Straight drift
- **WHEN** a cloud is at (0.5, 0.5) at time t with speed 0.04, drift 0, and the wind angle is π/2 for the next 10 seconds
- **THEN** the cloud is at (0.5, 0.9) 10 seconds later

#### Scenario: Wrap around
- **WHEN** a cloud of size 0.10 drifts downwards and its `y` passes 1.05
- **THEN** it continues from `y` −0.05 at the same `x`

#### Scenario: Drifting past the play area
- **WHEN** a cloud drifts to the right and leaves the play area at `x` 1
- **THEN** it keeps drifting to the right beyond `x` 1 and only re-enters on the left at `x` −1 − r after its `x` has passed 2 + r

#### Scenario: Same position on server and client
- **WHEN** the frontend computes a cloud's position from the latest level for some time within the schedule
- **THEN** the result equals the position the server uses for that cloud at that time

### Requirement: Clients draw the clouds
The frontend SHALL draw every cloud of the latest level as a soft, semi-transparent cloud of the
cloud's `size` (in play-area heights), above the flowers and bees, without catching clicks. Clouds
SHALL NOT be clipped at the edges of the play area: a cloud left or right of the play area SHALL be
drawn wherever the window shows that part of the sky. The frontend SHALL move each cloud
continuously to its position at the current server time, computed from the level's data as
specified above, and SHALL replace its clouds with those of every new level.

#### Scenario: Clouds drift on screen
- **WHEN** a player watches the meadow for a few seconds without any event arriving
- **THEN** the clouds move smoothly in the wind direction

#### Scenario: Clouds continue beside the play area
- **WHEN** a player on a landscape screen watches a cloud drift out of the play area to the side
- **THEN** the cloud stays visible and keeps drifting over the meadow beside the play area until it leaves the window

#### Scenario: Clouds of different sizes
- **WHEN** the level contains clouds with different `size` values
- **THEN** each cloud is drawn with its own diameter

#### Scenario: Clicking through a cloud
- **WHEN** the player clicks a spot covered by a cloud
- **THEN** the bee flies to that spot as with any other click
