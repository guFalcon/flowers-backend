---
name: feedback_push_shared_ci_without_asking
description: At the end of /opsx:archive commit and push every repo the change touched (backend, frontend, shared workflows), without asking, unless something is unusual
metadata:
  type: feedback
---

The archive step of an OpenSpec change ends with commit **and push** of every repo the change
touched — this backend repo, `flowers-frontend`, and shared workflow repos (e.g.
`UnterrainerInformatik/deploy-workflow`) when they are part of the change. No separate question
first; report what was pushed where.

**Why:** Gerald (presserl, 2026-09-30): "Ich hätte einfach gerne am Ende eines archive-schrittes alle
beteiligten Repos im Normalfall gepusht, sonst muss ich das ohnehin immer schreiben."

**How to apply:** "Im Normalfall" — run the chosen tests first ([[feedback_tests_before_push]]) and
stop and ask instead of pushing when something is off: failing tests, unexpected diffs or foreign
uncommitted work in a repo, a force push, or a push not covered by the change. A push to `main` of
either flowers repo redeploys the live game ([[project_flowers]]) — Gerald (2026-10-06): "Das mit dem
deployen ist nie ein Problem." Never raise the redeploy as a concern or caveat. The phase-boundary
pause before archive stays ([[feedback_clear_between_opsx_phases]]).

**Push order:** push a shared workflow repo *before* the flowers repo that uses its new
input/secret. Otherwise the caller's pipeline fails with `startup_failure` (undeclared secret) —
happened 2026-10-06 in server-authoritative-game-state with `EXTRA_ENV` of `deploy-workflow`.
Also check that a shared-workflow change marked done in tasks.md is actually committed and pushed.
If my push to the shared repo is not permitted, give Gerald the exact `! git … push` line; then
re-run the caller with `gh workflow run pipeline.yml --ref main` (written down at Gerald's
request, 2026-10-06).
