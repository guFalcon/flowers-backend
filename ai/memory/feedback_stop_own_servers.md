---
name: feedback_stop_own_servers
description: Stop every server/daemon/container Claude started before finishing; verify with ps/ss/docker instead of assuming
metadata:
  type: feedback
---

Every server instance Claude starts (`quarkus:dev`, the frontend's Express/nodemon server,
Playwright containers, compose stacks) must be stopped again when the work that needed it is done
— and the shutdown must be verified.

**Why:** Gerald found two of Claude's servers running in parallel (presserl, 2026-09-26) while
Claude believed none were running anymore.

**How to apply:** before finishing a turn that started servers, check `ps -eo pid,etime,cmd`,
`ss -ltnp` (8084, 8081, 8080) and `docker ps`; stop only what is Claude's own, leave foreign
processes and containers alone (Gerald often has a long-running dev server on 8080), then check
again. Before starting a server, check whether one of Claude's is still up.
