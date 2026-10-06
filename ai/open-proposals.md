# Open proposals

Drafted-but-not-yet-proposed work for backend and frontend. Delete an entry as soon as it becomes
an `/opsx:propose` change — never tick it off.

## Server trusts the client
Any client can harvest any flower from anywhere (no bee-position check), `POST /api/admin/restart` is
unprotected (frontend admin mode is just `?admin=true`), and the honey score lives only in the
browser (lost on reload). Decide what should be authoritative on the server — ties into the
leaderboard entry.

## Live leaderboard for all players
Requested by Gerald 2026-10-06: show every player a current leaderboard. Needs server-side honey per
bee (see "Server trusts the client"), a leaderboard in the level/SSE payload or its own event, and
a frontend panel. Open: player names/colours, reset on level restart. Contract change → both repos.

## Clouds that slow bees down
Requested by Gerald 2026-10-06: clouds on the meadow that slow bees flying through them, so players
learn to route around them. Open: static vs drifting clouds, server- or client-side flight time,
how harvest timing accounts for the slowdown. Probably after the frontend restructure.
