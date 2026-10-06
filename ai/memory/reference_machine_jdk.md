---
name: reference_machine_jdk
description: Default JDK on Gerald's machine is now 27 (breaks Lombok 1.18.38) — pin JAVA_HOME=/usr/lib/jvm/java-21-openjdk for Maven; Node via fnm/volta
metadata:
  type: reference
---

**Update 2026-10-06:** `archlinux-java` default is now `java-27-openjdk`; flowers-backend fails to
compile under it (Lombok getters/`log` missing). Run Maven as
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw …` (`mvnw` is executable in git since
fix-harvest-event-and-quick-fixes). The Bash tool runs zsh, not fish; `pkill -f` patterns must
not match their own command line (use `[q]uarkus:dev`). Older note: on Gerald's CachyOS machine the default JDK was 21 (`archlinux-java` default
`java-21-openjdk`, verified 2026-09-26 in presserl); JDK 26 is installed but not default, the
IntelliJ JBRs are 25. Lombok broke on anything past 21 in java-overmind-server, so plain `./mvnw`
works; pin `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` only if the default changes again. Node is
managed via fnm/volta (`openspec` is on the volta path).

flowers-backend targets `maven.compiler.release=21` (Quarkus 3.26.4) and uses Lombok 1.18.38; the
image is built from `src/main/docker/Dockerfile.jvm`. The frontend pins Node 20 (`.nvmrc`), its
image uses `node:22.14-alpine`. Commands: [[reference_build_and_test]].
