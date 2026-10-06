---
name: feedback_tests_welcome
description: User likes tests — write them freely when adding or changing behaviour, no need to ask first
metadata:
  type: feedback
---

Adding or extending tests alongside code changes does not need permission.

**Why:** Gerald said so explicitly. Tests are a default part of the work.

**How to apply:** Backend: JUnit 5 + AssertJ, `@QuarkusTest` + REST Assured (already in the pom;
see the `java-test-quality` and `tdd` skills). The frontend has no test setup — check its behaviour
with a headless browser ([[feedback_ui_tests_myself]]) unless a change introduces a test runner.
Cover the path you touched; no coverage bikeshedding. Never strip existing tests to simplify a
refactor without flagging it.
