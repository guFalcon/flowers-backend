---
name: feedback_ui_tests_myself
description: Click through UI checks (manual-check tasks) myself with a headless browser instead of handing them to Gerald
metadata:
  type: feedback
---

Manual UI checks (e.g. "manual check in dev mode" tasks in an OpenSpec change) are Claude's job:
drive the frontend with a headless browser (Playwright) and report the result, instead of listing
click steps for Gerald.

**Why:** stated by Gerald (presserl, 2026-09-27) after an apply ended with "7.2 please check manually".

**How to apply:** start backend (`quarkus:dev`, :8084) and frontend (:8081) locally, point the
frontend at the local backend (the `SERVER` constant in `index.html`, or route the live URL in
Playwright with `context.route` so no file changes), drive it with the Playwright container
described in [[reference_build_and_test]]. Several browser contexts simulate several players.
Only mark the task done when the scripted run showed the expected behaviour. Afterwards stop
every server you started, see [[feedback_stop_own_servers]].
