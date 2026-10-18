# Verification report — Helpful Workers 0.9.0

## Automated / performed here

- `./gradlew compileJava` and `./gradlew build` succeeded on Java 21.
- JAR: `build/libs/helpfulworkers-0.2.0.jar`
- Dedicated `runServer` smoke: mod listed as **Helpful Workers 0.2.0**, recipes loaded, server reached **Done**, no `ClientHooks` / `NoClassDefFoundError` during startup.
- Game-test source is present in `WorkerGameTests.java` (ownership, kits, clipboard cancel, cross-worker menu isolation, unsupported recipes, 0.1-style NBT). These were not executed in a game-test server run in this session.

## 0.8.0 follow-up: dialogue and greeting strings moved to lang keys — performed 2026-10-18

- `./gradlew build runGameTestServer`: build succeeded; **all 38 required game tests passed**, including `langKeysExist`, which now also covers 209 new keys (`gui.helpfulworkers.dialogue.*`, `line.helpfulworkers.*`, toast and greeting-format keys) via `Lang.all()`.
- Changed: `WorkerDialogueScreen` has no literal English left except the `✕` / `[OK]` glyphs, item names from the server and the raw id of an unlabelled crop; `WorkerLines.greeting` returns a `Component`; `ToastPayload` carries a `Component` (wire format change, client and server must be the same build).
- Not changed: `Worker.status` and `ActionResult` text stay English strings; page ids and action strings are untouched.
- Not played in a world: every dialogue page at GUI scales 1–4 (longer or shorter translated text can overflow a 252 px button), and the greeting toast in game.

## 0.9.0 (depth: levels, shared chests, town hall, personalities) — performed 2026-10-18

- `./gradlew build runGameTestServer`: build succeeded, JAR `build/libs/helpfulworkers-0.9.0.jar`; **all 45 required game tests passed** on four runs in a row (each run capped at 90 s).
- New tests: `statusTranslatesAndKeepsEnglish`, `levelsSpeedUpBreakingAndSurviveSave`, `sharedSupplyClaimsAvoidDoubleTrips`, `depositKeepsReserves`, `minerSealsWithCobbleFromOutput`, `townHallRecruitsWithContract`, `traitAssignedAndSaved`.
- Test harness fixes: the walking tests (`flatWalkNeverTeleports`, `walksThroughClosedDoorToChest`) failed intermittently because idle workers from other tests strolled into their plots; off-duty workers without a bed or hall now stay within 6 blocks of where they stopped, and idle strolling never interrupts a purposeful walk. `rosterSortsBlockedFirstWithDropTime` now waits for freshly spawned entities. The walking tests report a route-failure log on failure.
- Known harness issue: about one run in five before these fixes, some tests never started (the framework waits without a timeout for test chunks to load). Not seen in the last eight runs; capped runs make it visible instead of hanging.
- **UI screenshot pass** (`./gradlew runUiShots`, new dev-only run): opens the dev world "New World" and saves 11 screens at GUI scale 2 and 4 to `run/screenshots/hw_*.png` (dialogue Talk/Work/Where/Options for gatherer, rancher and courier/Needs/Build, equipment, overview, config). Reviewed; fixed a clipped "Recipes" tab (now "Craft"), clipped "+16 of held item" buttons (now "+16" with a tooltip) and Build/Blueprint help text hidden behind buttons. No exceptions in the client log.
- Not played: levelling up through real work, chatter between two idle workers, the new sounds, the town hall in a world (block model, recruit, sneak-assign), two workers sharing a chest for an hour (perf table), `/worker keep`.

## 0.8.0 (UI and polish) — performed 2026-10-18

- `./gradlew build runGameTestServer`: build succeeded, JAR `build/libs/helpfulworkers-0.8.0.jar`; **all 38 required game tests passed**.
- New tests: `langKeysExist` (every key in `Lang.all()`, each role name and kit line, each config label and every kit item name is in `en_us.json`), `rosterSortsBlockedFirstWithDropTime` (blocked worker listed first, server state byte, ticks since drop-off, `LastDeposit` saved), `displayConfigDefaults`.
- `runClient` smoke: the client reached the title screen with Helpful Workers 0.8.0 loaded; the log shows no exceptions or missing textures. No world was opened.
- Art: all 12 role icons and 11 kit sprites redrawn; no two texture files are identical any more (checked by hash and on a contact sheet).
- Not played in a world: the tab row and status strip at GUI scales 1–4, checkbox toggles, item icons on the Needs and Routes pages, the equipment screen model and hints, the overview's Talk and Go to buttons, the line-box outline while sneaking with the clipboard, and the config screen.

## 0.7.0 (gathering automation) — performed 2026-10-17

- `./gradlew build runGameTestServer`: build succeeded, JAR `build/libs/helpfulworkers-0.7.0.jar`; **all 35 required game tests passed**.
- New tests: `quarryDigsAndLaysCobbleFloor` (gravel and sand dug, cobble laid on the area's bottom layer, stone below untouched), `gathererCutsCaneAboveTheRoot` (live ticks: root kept, both upper stalks cut and swept into the bag), `rancherHarvestsHoneyOnlyOverCampfire` (full hive ignored without a campfire, bottled once a lit campfire sits below), `courierFiltersKeepsAndRotates` (filter accept/reject, keep 16, round-robin cursor), `smelterFetchesOnFootAndLoadsAStack` (nothing loaded from afar, supply trip, whole stack into the input slot, coal into the fuel slot, data-driven fuel), `requestProgressShowsInStatus` (status `· Request 10/64 Oak Log`, flags, NBT round trip, pause-when-done and keep-going).
- `flatWalkNeverTeleports` (0.5.0) timed out once in three runs on this machine and passed on the reruns; it is timing-sensitive and was not changed.
- Not played in a client: the Quarry button and depth picker, the gatherer kit/icon/outfit art, the Resources page for both roles, honey bottling in a real apiary, the courier Routes page (filter by held item, keep N), the courier furnace run, and the request list page and roster text.

## 0.6.0 (feel alive) — performed 2026-10-17

- `./gradlew build runGameTestServer`: build succeeded, JAR `build/libs/helpfulworkers-0.6.0.jar`; **all 29 required game tests passed**.
- New tests: `blockBreakingTakesTime`, `foresterChopsLogByLog`, `fellQueueSurvivesSave`, `overheadStatusSyncsToClientData`.
- Not played in a client: crack animation and hit sounds, overhead status colours and readability at GUI scales, outfit textures on wide and slim skins, greetings, off-duty wandering, dusk deposit and the morning stretch.

## 0.5.0 (real movement) — performed 2026-10-17

- `./gradlew build runGameTestServer`: build succeeded, JAR `build/libs/helpfulworkers-0.5.0.jar`; **all 25 required game tests passed**.
- New tests: `walksThroughClosedDoorToChest` (worker paths through a closed oak door to a chest, no teleport), `supplyTakeNeedsProximity` (`Jobs.take` fails from across the plot, starts a fetch, succeeds after standing beside the chest), `flatWalkNeverTeleports` (hop counter stays 0 on a flat plot), `standSpotIsBesideTarget`, `torchStepCountsAsDone`.
- Not played in a client: a forester crossing a fence line through a gate, the stuck-recovery ladder statuses on broken terrain, builders stepping aside mid-build, knights following across long distances, the smelter still loads furnaces from a remote supply read (left for a later phase).

## 0.4.0 (foundations) — performed 2026-10-17

- `./gradlew build runGameTestServer`: build succeeded, JAR `build/libs/helpfulworkers-0.4.0.jar`; **all 20 required game tests passed**.
- New tests: `statusWritesMarkDirty`, `knockOutThenRecoveryResumesWork`, `passiveRegenHealsEveryOtherWorkTick`, `minePlanSurvivesSave`.
- Not played in a client: live status refresh while watching a worker, resume after a mob knock-out, mine plan continuing after a world reload.

## 0.3.2 (builder ground prep) — performed 2026-10-17

- `./gradlew build` succeeded on the Gradle toolchain JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-…`); JAR: `build/libs/helpfulworkers-0.3.2.jar`.
- Game tests now run headless: `./gradlew runGameTestServer` (new `gameTestServer` run plus the `helpfulworkers:empty` 8×6×8 template). **All 16 required tests passed**, including the new `sitePrepClearsVegetationOnly`, `flattenPlotLevelsAndFills` and `sitePrepLegacySaveKeepsBuildGoing`.
- Two pre-existing tests only passed after harness fixes: the mock player now stands next to the chest in `assignOutputWhileWorking`, and payload sends are skipped for connections without the mod channel (`WorkerNetwork.send`), which also protects vanilla clients.
- Not played in a client: watching a builder clear a real tree-covered plot, flatten a slope, the Ground toggle/Flatten buttons in the dialogue, drop collection into the bag, and `/worker siteprep` / `/worker flatten`.

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
