---
name: reference_pihole_upstream_block
description: Runner offline with "Connection refused (broker.actions.githubusercontent.com)" = Pi-hole on greatmachine caches an upstream-blocked answer for 24h; fix is restarting the pihole container
metadata:
  type: reference
---

The LAN DNS 10.10.196.3 is Pi-hole v6 in docker container `pihole` on `ssh greatmachinen`
(upstreams 8.8.8.8/8.8.4.4). Its docker CLI is newer than the daemon: prefix commands with
`DOCKER_API_VERSION=1.43`.

**Symptom (2026-11-20):** self-hosted runners on babylon5 show offline in GitHub, jobs stay `queued`,
container log loops `√ Connected to GitHub` or shows
`Runner connect error: Connection refused (broker.actions.githubusercontent.com:443)`;
`getent hosts broker.actions.githubusercontent.com` on babylon5 returns `::` / `0.0.0.0`.
Pi-hole log (`/var/log/pihole/pihole.log` in the container): `blocked upstream with NXRA`.

**Cause:** one bad upstream reply (`blocked due to upstream response (header)`, 00:59 that night,
also hit mystrom/miele/solax/ntp domains). Pi-hole then answers that domain for that client from
its own cache for `dns.cache.upstreamBlockedTTL` = 86400 s without asking upstream again. Other
clients still resolve fine, which makes it look like client-specific filtering.

**Fix:** `pihole reloaddns` (SIGHUP) does NOT clear it, and `pihole restartdns` no longer exists in
v6. `docker restart pihole` (DNS back after ~1 s) clears it. Then `docker restart` the runner
containers on babylon5 so they leave the reconnect loop. Check that they are `online` with
`gh api repos/<owner>/<repo>/actions/runners`.

See [[reference_ci_runners]].
