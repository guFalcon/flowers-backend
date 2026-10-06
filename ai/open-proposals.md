# Open proposals

Drafted-but-not-yet-proposed work for backend and frontend. Delete an entry as soon as it becomes
an `/opsx:propose` change — never tick it off.

## Live leaderboard for all players
Requested by Gerald 2026-10-06: show every player a current leaderboard. The server already keeps
each bee's `honey` and sends it with every bee in the level (server-authoritative-game-state), and a
level restart resets it to 0. Still needed: a frontend panel (from the level's bees, or a dedicated
event if that is too coarse). Open: player names (bees only have an id and a colour). Contract
change → both repos.

## Clouds that slow bees down
Requested by Gerald 2026-10-06: clouds on the meadow that slow bees flying through them, so players
learn to route around them. Open: static vs drifting clouds, server- or client-side flight time,
how harvest timing accounts for the slowdown. Probably after the frontend restructure.
