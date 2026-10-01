# Verification status — Helpful Workers 0.3.1

## Current publication check — October 1, 2026

- Built the copied source with JDK 21 using `./gradlew build --no-daemon --console=plain` on macOS: **BUILD SUCCESSFUL**.
- Published JAR is built from the source in this repository: `build/libs/helpfulworkers-0.3.1.jar`.
- Build and mod metadata both declare version 0.3.1, Minecraft 1.21.1, and NeoForge 21.1.235 / compatible 21.1.x.
- Compilation emitted deprecation warnings; there were no compile errors.
- Gradle reported `test NO-SOURCE`. The embedded `WorkerGameTests` were **not executed**; build success is not a gameplay test pass.
- No new interactive client, dedicated-server startup, multiplayer, or Windows build checks were performed for this publication.
- README controls, role items, recipes, configuration defaults, and command examples were checked against the current source.

The 0.3.1 release is experimental. Verify survival behavior, navigation/mining hazards, inventory conservation, save compatibility, multiplayer ownership, and larger groups before relying on it in a main world.

## Historical report supplied with the project

The following report describes older 0.2.0 checks. It is retained as history and does not establish 0.3.1 gameplay acceptance.

# Verification report — Helpful Workers 0.2.0

## Automated / performed here

- `./gradlew compileJava` and `./gradlew build` succeeded on Java 21.
- JAR: `build/libs/helpfulworkers-0.2.0.jar`
- Dedicated `runServer` smoke: mod listed as **Helpful Workers 0.2.0**, recipes loaded, server reached **Done**, no `ClientHooks` / `NoClassDefFoundError` during startup.
- Game-test source is present in `WorkerGameTests.java` (ownership, kits, clipboard cancel, cross-worker menu isolation, unsupported recipes, 0.1-style NBT). These were not executed in a game-test server run in this session.

## Not played / not acceptance-complete

Interactive client and multiplayer checks from the handoff were **not** performed:

- Recruit and configure a worker through dialogue only
- All four role work cycles after dialogue setup
- Kit reuse vs contract consumption in survival
- Right-click never accidentally transfers items
- Inventory/equipment shift-click, full bags, death item conservation
- Clipboard invalid/cancel preserving assignments in play
- Two nearby workers never crossing actions in play
- Second player cannot modify another player's worker
- Save/restart including loading a 0.1 world
- Builder capture/overwrite/placement/rotations/preview/materials through dialogue
- GUI scale variants
- Command vs UI parity in survival play

Do not treat 0.2.0 as fully accepted until those gameplay checks are done.
